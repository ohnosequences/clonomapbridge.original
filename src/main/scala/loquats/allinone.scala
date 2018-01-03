package era7bio.asdfjkl.loquats

import era7bio.asdfjkl._, data._, loquats._
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

/*
The input sequence data are two paired-end datasets. The standard notation is (R1, R2) for a dataset.

Let's call the two datasets A and B, so that we have 4 read collections: (A.R1, A.R2), (B.R1, B.R2).
For both A and B, R1 reads are both better (quality-wise) and larger than those in R2.

Datasets are mixed in both R1 and R2 fastq files. We need to identify them based on their patterns (see below).
*/
case object allInOne {

  // Unfortunately ProductType doesn't have concatenation
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
    //
    |[AnyData]
  )

  case object dataProcessing extends DataProcessingBundle(
    umiAnalysis.dataProcessing.bundleDependencies ++
    igblastAnnotation.TRB.bundleDependencies : _*
  )(umiAnalysis.inputData, outputData) with LazyLogging {

    def instructions: AnyInstructions = say("Running UMI analysis and annotation")


    def readFile: File => String =
      file =>
        new String( java.nio.file.Files readAllBytes file.toPath )


    def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles } = {
      lazy val r1URI : S3Object =
        S3Object(new java.net.URI(readFile( context inputFile data.r1 )))
      lazy val r2URI : S3Object =
        S3Object(new java.net.URI(readFile( context inputFile data.r2 )))

      val r1File : File = File.createTempFile("read1", "fastq.gz");
      val r2File : File = File.createTempFile("read2", "fastq.gz");

      val tm = TransferManagerBuilder.standard()
        .withS3Client(s3.defaultClient.asJava)
        .build()

      util.Try { tm.download(r1URI.bucket, r1URI.key, r1File).waitForCompletion() } match {
        case scala.util.Success(file) => logger.info(s"Read 1 downloaded to $file")
        case scala.util.Failure(err)  => logger.error(s"Error downloading read 1: $err")
      }

      util.Try { tm.download(r2URI.bucket, r2URI.key, r2File).waitForCompletion() } match {
        case scala.util.Success(file) => logger.info(s"Read 2 downloaded to $file")
        case scala.util.Failure(err)  => logger.error(s"Error downloading read 2: $err")
      }

      logger.info(s"Reads downloaded to $r1File and $r2File")

      // FIXME: Check the downloads were successful

      val outputDir: File = context / "output"
      if (!outputDir.exists) Files.createDirectories(outputDir.toPath)

      val umiOuts = umiAnalysis.dataProcessing.Outs(outputDir)
      val annOuts = igblastAnnotation.TRB.Outs(outputDir)

      umiAnalysis.dataProcessing.processImpl(r1File, r2File, umiOuts) -&-
      igblastAnnotation.TRB.processImpl(umiOuts.consensus.fasta, annOuts) -&-
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
        //
        *[AnyDenotation { type Value <: FileResource }]
      )
    }
  }
}
