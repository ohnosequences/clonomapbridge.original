package era7bio.asdfjkl.bundles

import ohnosequences.statika._
import ohnosequences.awstools._, s3._
import java.io.File

abstract class IgblastBundle(version: String) extends Bundle() {

  private val s3folder = s3"resources.ohnosequences.com" / "igblast" / version /

  lazy val folder = new File(s3folder.key)
  lazy val binaries = Seq(
    "igblastn.bin",
    "igblastn"
  )

  def downloadFolder = LazyTry {
    val tm = S3Client().createTransferManager
    tm.download(s3folder, new File("."))
    tm.shutdown()
  }

  def linkBinaries = binaries.map { name: String =>
    val binaryPath = new File(folder, name).getCanonicalPath
    cmd("chmod")("+x", binaryPath) -&-
    cmd("ln")("-s", binaryPath, s"/usr/bin/${name}")
  }.reduce[AnyInstructions]( _ -&- _ )

  // FIXME: there should be a better solution for this (IgBLAST needs these folders at the place where you call it)
  def linkAuxFolders = {
    cmd("ln")("-s", new File(folder, "internal_data").getCanonicalPath, "internal_data") -&-
    cmd("ln")("-s", new File(folder, "optional_file").getCanonicalPath, "optional_file")
  }

  def instructions: AnyInstructions =
    downloadFolder -&-
    linkBinaries -&-
    linkAuxFolders
}

case object igblast extends IgblastBundle("1.7.0")
