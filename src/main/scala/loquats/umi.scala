package era7bio.asdfjkl.loquats

import era7bio.asdfjkl._, data._
import era7bio.repseqmiodx._, umi._, io._
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

  case object inputData extends DataSet(
    data.demultiplexed.r1 :×:
    data.demultiplexed.r2 :×:
    |[AnyData]
  )

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

    def instructions: AnyInstructions = say("Let the clustering begin")

    case class Outs(prefix: File) {
      import ohnosequences.loquat.utils.files._

      // Final consensus FASTA/FASTQ:
      val consensus = ReadsProcessing.OutputFiles(
        (prefix / "consensus").createDirectory
      )

      val report    = prefix / data.mig.size.report.label
      val histogram = prefix / data.mig.size.histogram.label
    }

    def processImpl(r1: File, r2: File, output: Outs): AnyInstructions { type Out <: OutputFiles } = {
      LazyTry {
        val readPairs: Iterator[ReadPair] =
          parseFromFastqPhred33LinesDropErrors(
            r1.gzippedLines,
            r2.gzippedLines
          )

        val consensuses: Array[UMIConsensus] =
          ReadsProcessing.writeConsensuses(
            format = DefaultFormats.typeA,
            reads  = readPairs,
            debug  = true
          )(output.consensus)

        val sizesMap = ReadsProcessing.clusterSizes(consensuses, Some(output.report))
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

    def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles } = {
      val outputDir: File = context / "output"
      if (!outputDir.exists) Files.createDirectories(outputDir.toPath)

      processImpl(
        context.inputFile(data.demultiplexed.r1),
        context.inputFile(data.demultiplexed.r2),
        Outs(outputDir)
      )
    }
  }
}
