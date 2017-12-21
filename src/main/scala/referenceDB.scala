package era7bio.asdfjkl

import ohnosequences.statika._
import ohnosequences.awstools._, s3._
import java.io.File
import era7bio.db._, tcr._

abstract class ReferenceDB(val geneType: GeneType) extends Bundle() {

  lazy val s3folder: S3Folder = tcr.data.blastDB(geneType)

  // NOTE: folder are downloaded with all S3 directory structure
  lazy val location: File = new File(s3folder.key)

  // NOTE: this is not a real file, it's folder + name-suffix
  lazy val name: File = new File(location, tcr.data.blastDBName(geneType))

  def instructions: AnyInstructions =
    LazyTry {
      val tm = S3Client().createTransferManager
      // we download it to ./, but it will add s3folder.key suffix
      tm.download(s3folder, new File(".")).get
      tm.shutdown()
    } -&-
    say(s"Downloaded reference database ${name.getCanonicalPath}")
}

abstract class AuxFile(val species: Species, val chain: Chain) extends Bundle() {

  lazy val s3obj: S3Object = tcr.data.igblastAux(species, chain)

  lazy val file: File = new File(s3obj.key)

  def instructions: AnyInstructions =
    LazyTry {
      val tm = S3Client().createTransferManager
      tm.download(s3obj, file).get
      tm.shutdown()
    } -&-
    say(s"Downloaded aux file to ${file.getCanonicalPath}")
}

case object referenceDBs {

  case object human {

    case object TRB {

      case object V extends ReferenceDB(GeneType(Species.human, Chain.TRB, Segment.V))
      case object D extends ReferenceDB(GeneType(Species.human, Chain.TRB, Segment.D))
      case object J extends ReferenceDB(GeneType(Species.human, Chain.TRB, Segment.J))

      case object aux extends AuxFile(Species.human, Chain.TRB)
    }

    case object TRA {

      case object V extends ReferenceDB(GeneType(Species.human, Chain.TRA, Segment.V))
      case object J extends ReferenceDB(GeneType(Species.human, Chain.TRA, Segment.J))

      case object aux extends AuxFile(Species.human, Chain.TRA)
    }
  }
}
