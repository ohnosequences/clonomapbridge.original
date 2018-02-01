package era7bio.repseqmiodx.bundles

import ohnosequences.statika._

case object r extends Bundle() {

  def instructions: AnyInstructions =
    cmd("yum")("install", "-y", "R") -&-
    cmd("R")("-e",
      """install.packages(c("ape", "seqinr"), repos="https://cloud.r-project.org/")
        |source("http://www.bioconductor.org/biocLite.R")
        |biocLite("msa")
        |library("msa")
        |library("ape")
        |library("seqinr")
        """
    )
}
