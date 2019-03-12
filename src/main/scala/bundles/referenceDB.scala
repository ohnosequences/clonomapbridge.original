package ohnosequences.clonomapbridge.bundles

import ohnosequences.statika._
import ohnosequences.awstools._, s3._
import java.io.File
import ohnosequences.db._, tcr._

/**
 * Abstract bundle for downloading a reference database parametrized by its
 * gene type
 *
 * The instructions of this bundle download the database locally replicating
 * the object key structure.
 */
abstract class ReferenceDB(val geneType: GeneType) extends Bundle() {

  /** Define the S3 directory where the DB files live */
  lazy val s3folder: S3Folder = tcr.data.blastDB(geneType)

  /**
   * The location where the DB files will be downlaoded replicate the S3
   * directory structure from the current directory
   */
  lazy val location: File = new File(s3folder.key)

  /** Not a real file, just folder + name-suffix, used only for logging */
  lazy val name: File = new File(location, tcr.data.blastDBName(geneType))

  /**
   * Define the instructions of the bundle
   *
   * Create an S3 client and download the S3 directory to the local directory
   */
  def instructions: AnyInstructions =
    LazyTry {
      val tm = S3Client().createTransferManager
      // we download it to ./, but it will add s3folder.key suffix
      tm.download(s3folder, new File(".")).get
      tm.shutdown()
    } -&-
    say(s"Downloaded reference database ${name.getCanonicalPath}")
}


/**
 * Abstract bundle for downloading the auxiliary file for a Species (human,
 * mouse...) and a Chain (TRA, TRB)
 *
 * The instructions of this bundle download the file replicating the object key
 * structure.
 */
abstract class AuxFile(val species: Species, val chain: Chain) extends Bundle()
{

  /** The S3 path of the file to be downloaded */
  lazy val s3obj: S3Object = tcr.data.igblastAux(species, chain)

  /**
   * The location where the auiliary file will be downlaoded replicate the S3
   * directory structure from the current directory
   */
  lazy val file: File = new File(s3obj.key)

  /**
   * Define the instructions of the bundle
   *
   * Create an S3 client and download the S3 object to the local file
   */
  def instructions: AnyInstructions =
    LazyTry {
      val tm = S3Client().createTransferManager
      tm.download(s3obj, file).get
      tm.shutdown()
    } -&-
    say(s"Downloaded aux file to ${file.getCanonicalPath}")
}

/**
 * Collection of known reference databases:
 * Human TCR-β (V,D,J)
 * Human TCR-α (V.J)
 */
case object referenceDBs {

  case object human {

    case object TRB {

      case object V extends ReferenceDB(
        GeneType(Species.human, Chain.TRB, Segment.V)
      )
      case object D extends ReferenceDB(
        GeneType(Species.human, Chain.TRB, Segment.D)
      )
      case object J extends ReferenceDB(
        GeneType(Species.human, Chain.TRB, Segment.J)
      )

      case object aux extends AuxFile(Species.human, Chain.TRB)
    }

    case object TRA {

      case object V extends ReferenceDB(
        GeneType(Species.human, Chain.TRA, Segment.V)
      )
      case object J extends ReferenceDB(
        GeneType(Species.human, Chain.TRA, Segment.J)
      )

      case object aux extends AuxFile(Species.human, Chain.TRA)
    }
  }
}
