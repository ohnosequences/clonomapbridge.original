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
import org.ddahl.rscala.RClient

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
        val client = RClient(serializeOutput = true)
        val inputPath = productiveClonotypesTSV.getCanonicalPath()
        val outputPath = output(data.viz.phylogeneticTree).getCanonicalPath()
        client eval raw"""
          # Load needed libraries
          library("msa")
          library("ape")
          library("seqinr")

          # Retrieve paths
          inputFile  <- "$inputPath"
          outputFile <- "$outputPath"
          fastaFile  <- "aux.fasta"

          # Generate a command to parse the TSV file and generate a FASTA
          # ordered by the frequencies
          command <- paste(
            # Remove the header from the TSV
            paste("tail -n +2 ", inputFile),
            "sort -t$$'\t' -k3 -nr",
            # Keep the N first lines (otherwise it's too crowded)
            "head -n25",
            # Output the information as FASTA with Amino-acid seq-s + rounded frequencies as headers
            "awk -F'\t' '{ printf \">%s - %.2f\\n%s\\n\", $$6, $$4, $$5 }'",
            sep=" | "
          )

          # Run the command and redirects the output to
          system(
            paste(
              command,
              fastaFile,
              sep=" > "
            )
          )

          # Align the CDR3 sequences
          sequences <- readAAStringSet(fastaFile)
          alignment <- msaMuscle(sequences)

          # Convert the alignment to something seqinr understands
          align.seq <- msaConvert(alignment, type="seqinr::alignment")

          # Compute the alignment distances and generate the tree
          tree      <- nj(dist.alignment(align.seq, "identity"))

          # Plot the tree to the output file
          png(filename=outputFile)
          plot(tree)
          dev.off()

          # Remove auxiliary files
          system(paste("rm ", fastaFile))
        """
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
