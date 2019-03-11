package ohnosequences.clonomapbridge.loquats

import ohnosequences.clonomapbridge._, data._
import ohnosequences.repseqmiodx._, umi._, io._, clonotypes._
import ohnosequences.cosas._, types._, records._, klists._
import ohnosequences.loquat._
import ohnosequences.statika._
import ohnosequences.fastarious._, fasta._
import ohnosequences.reads._, paired._
import ohnosequences.datasets._
import ohnosequences.blast.api.parse.igblastn._
import ohnosequences.blast.api.IgBLASTOrganism
import scala.util.matching.Regex
import java.nio.file.Files
import java.io.File
import ohnosequences.db.tcr.{Chain, Species}
import sys.process._
import ohnosequences.clonomapbridge.bundles.referenceDBs.human
import com.typesafe.scalalogging.LazyLogging

/** == Annotation Loquat ==

*/
case object igblastAnnotation {

  // The input is just the consensus fasta from the umi step
  case object inputData extends DataSet(
    data.consensus.fasta :×:
    data.species :×:
    data.chain :×:
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
    human.TRB.V,
    human.TRB.D,
    human.TRB.J,
    human.TRB.aux,
    human.TRA.V,
    human.TRA.J,
    human.TRA.aux
  )(inputData, outputData) with LazyLogging {

    /**
     * Dummy instructions, the fun happens in processImpl
     */
    def instructions: AnyInstructions =
      say("Human TCR beta annotation")

    /**
     * Create the output local files from a base directory
     */
    case class Outs(prefix: File) {
      def apply(d: FileData): File = new File(prefix, d.label)
    }

    /**
     * Implementation of the process itself. Loquat will call the process
     * functions below
     */
    def processImpl(
      consensusFile: File,
      geneType: Option[(Species, Chain)],
      output: Outs
    ): AnyInstructions {
      type Out <: OutputFiles
    } = {
      LazyTry {
        // Get the species and chain from the optional geneType.
        // Yes, I know, I am using .get on an Option, but we *do* want to fail
        // if it is not defined. And we are protected by the wrapping LazyTry
        val (species, chain) = geneType.get

        val organism = species match {
          case Species.human => IgBLASTOrganism.human
          case Species.mouse => IgBLASTOrganism.mouse
        }

        val (dbVFile, dbDFile, dbJFile) =
          species match {
            case Species.human =>
              chain match {
                case Chain.TRA =>
                  (human.TRA.V.name, human.TRB.D.name, human.TRA.J.name)
                case Chain.TRB =>
                  (human.TRB.V.name, human.TRB.D.name, human.TRB.J.name)
              }
            case Species.mouse =>
              (
                new File(bundles.igblast.folder, "database/mouse_gl_V"),
                new File(bundles.igblast.folder, "database/mouse_gl_D"),
                new File(bundles.igblast.folder, "database/mouse_gl_J")
              )
          }

        logger.debug(s"Using $species-$chain databases:")
        logger.debug(s"    => V: $dbVFile")
        logger.debug(s"    => D: $dbDFile")
        logger.debug(s"    => J: $dbJFile")

        val auxFile = species match {
          case Species.human =>
            chain match {
              case Chain.TRA => human.TRA.aux.file
              case Chain.TRB => human.TRB.aux.file
            }

          case Species.mouse =>
            new File(bundles.igblast.folder, "optional_file/mouse_gl.aux")
        }

        logger.debug(s"    => Aux file: $auxFile")

        // Define the output for IgBLAST
        val igblastnOut = output(data.clonotype.igblastOut)

        // Define the IgBLAST command with the needed parameters
        val igblastnCmd = annotation.clonotypesCmd(
          queryFile = consensusFile,
          dbVFile = dbVFile,
          dbDFile = dbDFile,
          dbJFile = dbJFile,
          auxFile = auxFile,
          chain   = chain,
          output  = igblastnOut,
          igblastOrganism = organism
        )

        logger.debug(igblastnCmd.mkString(" "))

        // Run the IgBLAST command
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

        // Get the Clonotype Summary
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

    /**
     * Define the output directory and call processImpl
     */
    def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles } = {
      val outputDir: File = context / "output"
      if (!outputDir.exists) Files.createDirectories(outputDir.toPath)

      // Retrieve the specified reference DB
      val speciesFilePath = context.inputFile(data.species).toPath
      val chainFilePath = context.inputFile(data.chain).toPath
      val speciesString = new String(Files.readAllBytes(speciesFilePath))
      val chainString = new String(Files.readAllBytes(chainFilePath))
      val referenceDB =
        for {
          species <- Species.fromString(speciesString)
          chain <- Chain.fromString(chainString)
        } yield {
          (species, chain)
        }

      processImpl(
        context.inputFile(data.consensus.fasta),
        referenceDB,
        Outs(outputDir)
      )
    }
  }
}
