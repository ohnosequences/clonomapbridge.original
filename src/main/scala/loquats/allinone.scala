package ohnosequences.clonomap-bridge.loquats

import ohnosequences.clonomap-bridge._, data._, loquats._
import era7bio.repseqmiodx._, umi._, io._
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.transfer._
import ohnosequences.awstools._, s3._
import ohnosequences.cosas._, types._, records._, klists._
import ohnosequences.loquat._, utils.files._
import ohnosequences.statika._
import ohnosequences.fastarious._, fastq._
import ohnosequences.reads._, paired._
import ohnosequences.datasets._
import scala.util.matching.Regex
import java.nio.file.Files
import java.io.File
import com.typesafe.scalalogging.LazyLogging

/**
 * The input sequence data are two paired-end datasets. The standard notation
 * is (R1, R2) for a dataset.
 *
 * Let's call the two datasets A and B, so that we have 4 read collections:
 * (A.R1, A.R2), (B.R1, B.R2). For both A and B, R1 reads are both better
 * (quality-wise) and larger than those in R2.
 *
 * Datasets are mixed in both R1 and R2 fastq files. We need to identify them
 * based on their patterns (see below).
 */
case object allInOne {

  // Unfortunately ProductType doesn't have concatenation
  /**
   * Define all the output data of the whole pipeline (see data.scala for the
   * types)
   */
  case object outputData extends DataSet(
    // umi analysis step:
    data.mig.size.report        :×:
    data.mig.size.histogram     :×:
    data.consensus.pairs.joined :×:
    data.consensus.pairs.empty  :×:
    data.consensus.fasta        :×:
    data.consensus.fastq        :×:
    // annotation step:
    data.clonotype.totals                :×:
    data.clonotype.igblastOut            :×:
    data.clonotype.igblastTSV            :×:
    data.clonotype.igblastProductiveTSV  :×:
    data.clonotype.igblastProductiveJSON :×:
    // phylogenetic tree step:
    data.viz.phylogeneticTree :×:
    |[AnyData]
  )

  /**
   * Define how to process the input data to generate the output
   *
   * The parameters passed to DataProcessingBundle are the dependencies of this
   * Bundle, so they are installed before this runs
   */
  case object dataProcessing extends DataProcessingBundle(
    umiAnalysis.dataProcessing.bundleDependencies ++
    igblastAnnotation.TRB.bundleDependencies      ++
    visualizations.dataProcessing.bundleDependencies : _*
  )(umiAnalysis.inputData, outputData) with LazyLogging {

    /**
     * Dummy instructions, the fun happens in process
     */
    def instructions: AnyInstructions = say(
      "Running UMI analysis, annotation and phylogenetic tree creation"
    )

    /**
     * Auxiliary function to read the contents of a file to a string
     */
    def readFile: File => String =
      file =>
        new String( java.nio.file.Files readAllBytes file.toPath )

    /**
     * This is what the machine will execute once this bundle starts to run
     */
    def process(context: ProcessingContext[Input]): AnyInstructions {
      type Out <: OutputFiles
    } = {
      // Parse the contents of the messages delivered by loquat as input files:
      // they contain the paths to R1 and R2
      lazy val r1URI : S3Object =
        S3Object(new java.net.URI(readFile( context inputFile data.r1 )))
      lazy val r2URI : S3Object =
        S3Object(new java.net.URI(readFile( context inputFile data.r2 )))

      // Define an output directory to store the output of the whole pipeline
      val outputDir: File = context / "output"
      if (!outputDir.exists) Files.createDirectories(outputDir.toPath)

      // These are the local paths where the reads are being downloaded
      val r1File : File = outputDir / "read1.fastq.gz";
      val r2File : File = outputDir / "read2.fastq.gz";

      // The transfer manager to download the reads
      val tm = TransferManagerBuilder.standard()
        .withS3Client(s3.defaultClient.asJava)
        .build()

      // Try to download the R1 file
      util.Try {
        tm.download(r1URI.bucket, r1URI.key, r1File).waitForCompletion()
      } match {
        case scala.util.Success(_) =>
          logger.info(s"Read 1 downloaded to $r1File")
        case scala.util.Failure(e) =>
          logger.error(s"Error downloading read 1: $e")
      }

      // Try to download the R2 file
      util.Try {
        tm.download(r2URI.bucket, r2URI.key, r2File).waitForCompletion()
      } match {
        case scala.util.Success(_) =>
          logger.info(s"Read 2 downloaded to $r2File")
        case scala.util.Failure(e) =>
          logger.error(s"Error downloading read 2: $e")
      }

      // Declare the output files, local to the output dir, of each step
      val umiOuts = umiAnalysis.dataProcessing.Outs(outputDir)
      val annOuts = igblastAnnotation.TRB.Outs(outputDir)
      val phyOuts = visualizations.dataProcessing.Outs(outputDir)

      // Run everything:
      //   1. UMI Analysis
      //   2. IgBLAST annotation
      //   3. Phylogenetic tree creation
      // If everything worked as expected, link the output data to the
      // corresponding output files and Loquat would manage their upload
      umiAnalysis.dataProcessing.processImpl(r1File, r2File, umiOuts) -&-
      igblastAnnotation.TRB.processImpl(umiOuts.consensus.fasta, annOuts) -&-
      visualizations.dataProcessing.processImpl(
        annOuts(data.clonotype.igblastProductiveTSV),
        phyOuts
      ) -&-
      success("All outputs together",
        // UMI analysis out:
        data.mig.size.report(umiOuts.report)                        ::
        data.mig.size.histogram(umiOuts.histogram)                  ::
        data.consensus.pairs.joined(umiOuts.consensus.pairs.joined) ::
        data.consensus.pairs.empty(umiOuts.consensus.pairs.empty)   ::
        data.consensus.fasta(umiOuts.consensus.fasta)               ::
        data.consensus.fastq(umiOuts.consensus.fastq)               ::
        // annotation out:
        data.clonotype.totals(annOuts(data.clonotype.totals))                               ::
        data.clonotype.igblastOut(annOuts(data.clonotype.igblastOut))                       ::
        data.clonotype.igblastTSV(annOuts(data.clonotype.igblastTSV))                       ::
        data.clonotype.igblastProductiveTSV(annOuts(data.clonotype.igblastProductiveTSV))   ::
        data.clonotype.igblastProductiveJSON(annOuts(data.clonotype.igblastProductiveJSON)) ::
        // Visualizations out:
        data.viz.phylogeneticTree(phyOuts(data.viz.phylogeneticTree)) ::
        //
        *[AnyDenotation { type Value <: FileResource }]
      )
    }
  }
}
