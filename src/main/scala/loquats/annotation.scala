package era7bio.asdfjkl.loquats

import era7bio.asdfjkl._, data._
import era7bio.repseqmiodx._, umi._, io._, clonotypes._
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

/** == Annotation Loquat ==

*/
case object igblastAnnotation {

  case object inputData extends DataSet(
    data.consensus.fasta :×:
    |[AnyData]
  )

  /** == Annotation output ==

    We have

    - [[data.clonotype.totals]]
    - [[data.clonotype.igblastOut]]
    - [[data.clonotype.igblastTSV]]
    - [[data.clonotype.igblastProductiveTSV]]
    - [[data.clonotype.igblastProductiveJSON]]
  */
  case object outputData extends DataSet(
    data.clonotype.totals                :×:
    data.clonotype.igblastOut            :×:
    data.clonotype.igblastTSV            :×:
    data.clonotype.igblastProductiveTSV  :×:
    data.clonotype.igblastProductiveJSON :×:
    |[AnyData]
  )

  /** == Human TCR β annotation loquat ==

    This loquat runs the `igblastn` command specified in [[annotation]] and outputs clonotypes summary in dfferent formats; see [[outputData]].
  */
  case object TRB extends DataProcessingBundle(
    bundles.igblast,
    referenceDBs.human.TRB.V,
    referenceDBs.human.TRB.D,
    referenceDBs.human.TRB.J,
    referenceDBs.human.TRB.aux
  )(inputData, outputData) {

    def instructions: AnyInstructions =
      say("Human TCR beta annotation")

    case class Outs(prefix: File) {
      def apply(d: FileData): File = new File(prefix, d.label)
    }

    def processImpl(consensusFile: File, output: Outs): AnyInstructions { type Out <: OutputFiles } = {
      LazyTry {
        val igblastnOut = output(data.clonotype.igblastOut)

        val igblastnCmd = annotation.clonotypesCmd(
          queryFile = consensusFile,
          dbVFile = referenceDBs.human.TRB.V.name,
          dbDFile = referenceDBs.human.TRB.D.name,
          dbJFile = referenceDBs.human.TRB.J.name,
          auxFile = referenceDBs.human.TRB.aux.file,
          output  = igblastnOut
        )
        println(igblastnCmd.mkString(" "))

        val igblastnOutCode = igblastnCmd.!
        if(igblastnOutCode != 0)
          sys.error(s"igblastn could not execute correctly: ${igblastnOutCode}")

        // Write total numbers TSV
        Totals.parseFromLines(igblastnOut.lines).foreach { totals =>
          output(data.clonotype.totals).writeLines(Seq(
            Totals.TSVHeader,
            totals.toTSV
          ))
        }

        val summariesOpts: Seq[Option[ClonotypeSummary]] =
          ClonotypeSummary.parseFromLines(igblastnOut.lines).toSeq

        // check output files
        // if is empty is fine, it could be
        if(summariesOpts.exists(_.isEmpty))
          sys.error("igblastn generated a malformed clonotype output")

        val summaries: Seq[ClonotypeSummary] =
          summariesOpts.flatten
        // write all clonotypes tsv
        output(data.clonotype.igblastTSV).writeLines(
          Iterator(ClonotypeSummary.TSVHeader) ++
          summaries.map(_.toTSV)
        )

        val productiveSummaries: Seq[ClonotypeSummary] =
          summaries.onlyProductive
        // write all productive clonotypes tsv
        output(data.clonotype.igblastProductiveTSV).writeLines(
          Iterator(ClonotypeSummary.TSVHeader) ++
          productiveSummaries.map(_.toTSV)
        )

        val clonotypes: Iterator[Clonotype] =
          Clonotype.parseFromLines(igblastnOut.lines).flatten
        // write all productive clonotypes JSON
        output(data.clonotype.igblastProductiveJSON).writeAsJSONArray(
          productiveSummaries.toJSON(clonotypes).iterator
        )
      } -&-
      success("clonotype annotation finished",
        data.clonotype.totals(output(data.clonotype.totals))                               ::
        data.clonotype.igblastOut(output(data.clonotype.igblastOut))                       ::
        data.clonotype.igblastTSV(output(data.clonotype.igblastTSV))                       ::
        data.clonotype.igblastProductiveTSV(output(data.clonotype.igblastProductiveTSV))   ::
        data.clonotype.igblastProductiveJSON(output(data.clonotype.igblastProductiveJSON)) ::
        *[AnyDenotation { type Value <: FileResource }]
      )
    }

    def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles } = {
      val outputDir: File = context / "output"
      if (!outputDir.exists) Files.createDirectories(outputDir.toPath)

      processImpl(
        context.inputFile(data.consensus.fasta),
        Outs(outputDir)
      )
    }
  }
}
