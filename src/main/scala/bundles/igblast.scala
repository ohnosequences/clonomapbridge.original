package ohnosequences.clonomapbridge.bundles

import ohnosequences.statika._
import ohnosequences.awstools._, s3._
import java.io.File

/**
 * Bundle for downloading and installing IgBLAST, parametrized by its version
 */
abstract class IgblastBundle(version: String) extends Bundle() {

  /**
   * The S3 location of the IgBLAST binaries
   */
  private val s3folder = s3"resources.ohnosequences.com" / "igblast" / version /

  /**
   * Local directory where to download the binaries; it replicates the S3
   * directory structure
   */
  lazy val folder = new File(s3folder.key)

  /** Name of the binaries */
  lazy val binaries = Seq(
    "igblastn.bin",
    "igblastn"
  )

  /**
   * Download the whole S3 directory to the current directory
   */
  def downloadFolder = LazyTry {
    val tm = S3Client().createTransferManager
    tm.download(s3folder, new File("."))
    tm.shutdown()
  }

  /**
   * Create a symbolic link to the binary from a file that is in PATH; in this
   * case, /usr/bin/binary_name.
   */
  def linkBinaries = binaries.map { name: String =>
    val binaryPath = new File(folder, name).getCanonicalPath
    cmd("chmod")("+x", binaryPath) -&-
    cmd("ln")("-s", binaryPath, s"/usr/bin/${name}")
  }.reduce[AnyInstructions]( _ -&- _ )

  /**
   * IgBLAST needs these folders at the place where you call it
   */
  def linkAuxFolders = {
    cmd("ln")("-s", new File(folder, "internal_data").getCanonicalPath, "internal_data") -&-
    cmd("ln")("-s", new File(folder, "optional_file").getCanonicalPath, "optional_file")
  }

  /**
   * Define the instructions of the bundle: download the binaries and link
   * everything so the igblast command works from everywhere
   */
  def instructions: AnyInstructions =
    downloadFolder -&-
    linkBinaries -&-
    linkAuxFolders
}

/**
 * Actual instance of the igblast bundle, using version 1.7.0
 */
case object igblast extends IgblastBundle("1.7.0")
