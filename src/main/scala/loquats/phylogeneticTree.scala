package era7bio.asdfjkl.loquats

import era7bio.asdfjkl._, data._
import era7bio.repseqmiodx, repseqmiodx._, umi._, io._, clonotypes._
import ohnosequences.cosas._, types._, records._, klists._
import ohnosequences.loquat._
import ohnosequences.statika._
import ohnosequences.fastarious._, fasta._
import ohnosequences.reads._, paired._
import ohnosequences.datasets._
import ohnosequences.blast.api.parse.igblastn._
import scala.util.matching.Regex
import java.nio.file.Files
import java.io.File
import era7bio.db.tcr.GeneType
import sys.process._

/** == Visualizations Loquat ==

*/
case object visualizations {

  case object inputData extends DataSet(
    data.clonotype.igblastProductiveTSV :×:
    |[AnyData]
  )

  /** == Visualizations output ==

    We have

    - [[data.viz.phylogeneticTree]] : A PNG with the tree plot.
  */
  case object outputData extends DataSet(
    data.viz.phylogeneticTree :×:
    |[AnyData]
  )

  /** == Human TCR β annotation loquat ==

    This loquat aligns the CDR3 sequences with msa R package
    and plot a phylogenetic tree built with seqinr/ape R packages.
  */
  case object dataProcessing extends DataProcessingBundle(
    bundles.r
  )(inputData, outputData) {

    def instructions: AnyInstructions =
      say("Phylogenetic tree creation")

    case class Outs(prefix: File) {
      def apply(d: FileData): File = new File(prefix, d.label)
    }

    def processImpl(productiveClonotypesTSV: File, output: Outs)
    : AnyInstructions { type Out <: OutputFiles } = {
      LazyTry {
        repseqmiodx.phylogeneticTree.generate(
          productiveClonotypesTSV,
          output(data.viz.phylogeneticTree)
        )
      } -&-
      success("All visualizations created",
        data.viz.phylogeneticTree(output(data.viz.phylogeneticTree)) ::
        *[AnyDenotation { type Value <: FileResource }]
      )
    }

    def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles } = {
      val outputDir: File = context / "output"
      if (!outputDir.exists) Files.createDirectories(outputDir.toPath)

      processImpl(
        context.inputFile(data.clonotype.igblastProductiveTSV),
        Outs(outputDir)
      )
    }
  }
}
