package ohnosequences.clonomapbridge.bundles

import ohnosequences.statika._

/**
 * Bundle for downloading and installing R and the R libraries `ape` `seqinr`
 * and `msa`
 */
case object r extends Bundle() {

  def instructions: AnyInstructions =
    cmd("yum")("install", "-y", "R") -&-
    // The packages below need to link libgfortran and libquadmath to compile
    cmd("ln")("-s",
      "/usr/lib64/libgfortran.so.3",
      "/usr/lib/libgfortran.so"
    ) -&-
    cmd("ln")("-s",
      "/usr/lib64/libquadmath.so.0",
      "/usr/lib/libquadmath.so"
    ) -&-
    cmd("R")("-e",
      "install.packages(c('ape', 'seqinr'), repos='https://cloud.r-project.org/')"
    ) -&-
    cmd("R")("-e",
      "source('http://www.bioconductor.org/biocLite.R'); biocLite('msa', suppressUpdates=TRUE)")
}
