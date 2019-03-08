package ohnosequences.clonomapbridge.loquats

import ohnosequences.clonomapbridge._, data._
import ohnosequences.repseqmiodx._, umi._, io._
import ohnosequences.cosas._, types._, records._, klists._
import ohnosequences.loquat._
import ohnosequences.statika._
import ohnosequences.fastarious._, fastq._
import ohnosequences.reads._, paired._
import ohnosequences.datasets._
import scala.util.matching.Regex
import java.nio.file.Files
import java.io.File

/*
The input sequence data are two paired-end datasets. The standard notation is (R1, R2) for a dataset.

Let's call the two datasets A and B, so that we have 4 read collections: (A.R1, A.R2), (B.R1, B.R2).
For both A and B, R1 reads are both better (quality-wise) and larger than those in R2.

Datasets are mixed in both R1 and R2 fastq files. We need to identify them based on their patterns (see below).
*/
case object umiAnalysis {

  // Define the input data resources, which are the raw reads
  case object inputData extends DataSet(
    data.r1 :×:
    data.r2 :×:
    data.species :×:
    data.chain :×:
    |[AnyData]
  )

  // Define the output data resources
  case object outputData extends DataSet(
    data.mig.size.report        :×:
    data.mig.size.histogram     :×:
    data.consensus.pairs.joined :×:
    data.consensus.pairs.empty  :×:
    data.consensus.fasta        :×:
    data.consensus.fastq        :×:
    |[AnyData]
  )

  case object dataProcessing extends DataProcessingBundle()(inputData, outputData) {

    /**
     * Dummy instructions, all the fun happens in process and processImpl
     */
    def instructions: AnyInstructions = say("Let the clustering begin")

    // Define the output files, local to a base directory
    case class Outs(prefix: File) {
      import ohnosequences.loquat.utils.files._

      // Final consensus FASTA/FASTQ:
      val consensus = ReadsProcessing.OutputFiles(
        (prefix / "consensus").createDirectory
      )

      // One of these two files may not be created; we make sure they exist so
      // Loquat does not complain about not found files.
      consensus.pairs.joined.createNewFile()
      consensus.pairs.empty.createNewFile()

      val report    = prefix / data.mig.size.report.label
      val histogram = prefix / data.mig.size.histogram.label
    }

    def processImpl(r1: File, r2: File, output: Outs): AnyInstructions {
      type Out <: OutputFiles
    } = {
      LazyTry {
        // Read the lines from the files and parse the reads from the lines
        val readPairs: Iterator[ReadPair] =
          parseFromFastqPhred33LinesDropErrors(
            r1.gzippedLines,
            r2.gzippedLines
          )

        // Generate and write the consensuses
        val consensuses: Array[UMIConsensus] =
          ReadsProcessing.writeConsensuses(
            format         = DefaultFormats.typeA,
            reads          = readPairs,
            expectedLength = 330,
            debug          = true
          )(output.consensus)

        // Generate the sizes map
        val sizesMap = ReadsProcessing.clusterSizes(
          consensuses,
          Some(output.report)
        )
        ReadsProcessing.clusterSizesHistogram(sizesMap, Some(output.histogram))
      } -&-
      success("",
        data.mig.size.report(output.report)                        ::
        data.mig.size.histogram(output.histogram)                  ::
        data.consensus.pairs.joined(output.consensus.pairs.joined) ::
        data.consensus.pairs.empty(output.consensus.pairs.empty)   ::
        data.consensus.fasta(output.consensus.fasta)               ::
        data.consensus.fastq(output.consensus.fastq)               ::
        *[AnyDenotation { type Value <: FileResource }]
      )
    }

    /**
     * Define the output files and call processImpl
     */
    def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles } = {
      val outputDir: File = context / "output"
      if (!outputDir.exists) Files.createDirectories(outputDir.toPath)

      processImpl(
        context.inputFile(data.r1),
        context.inputFile(data.r2),
        Outs(outputDir)
      )
    }
  }
}
