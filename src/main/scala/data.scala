package ohnosequences.clonomapbridge

import ohnosequences.datasets._
import ohnosequences.cosas._, types._, klists._

case object data {

  abstract class R1R2(prefix: String)(extension: String) {
    case object r1 extends FileData(s"${prefix}.R1")(extension)
    case object r2 extends FileData(s"${prefix}.R2")(extension)
  }

  /* ## 1. Raw reads QA, trimming and filtering */

  /* ### Input reads */
  // case object demultiplexed extends R1R2("demultiplexed")("fastq.gz")
  case object r1 extends Data("r1")
  case object r2 extends Data("r2")

  /* ### Output */

  case object qaReport {
    /* - **1.1** Raw reads QA files */
    case object raw     extends R1R2("qa_report_raw")("html")
    /* - **1.3** QA file of preprocessed reads */
    case object trimmed extends R1R2("qa_report_trimmed")("html")
  }
  /* - **1.2** FASTQ files with preprocessed reads */
  case object trimmed {
    case object   paired extends R1R2("trimmed_paired")("fastq.gz")
    case object unpaired extends R1R2("trimmed_unpaired")("fastq.gz")
  }

  /* ## 2. UMI clustering and MIGs size filtering */

  /* ### Input

     - output **1.2**: `trimmedReads`
  */

  /* ### Output */
  case object mig {

    /* - **2.1** MIG sequences */
    case object clusters extends Data("clusters")
    case object errors   extends Data("errors")

    case object size {

      /* - **2.2** MIGs size report */
      case object report    extends FileData("mig_size")("tsv")
      /* - **2.3** MIGs histogram */
      case object histogram extends FileData("mig_size_histogram")("tsv")
    }

    // This will be generated from the output of clusters (one task for each cluster)
    case object cluster  extends R1R2("MIG")("fastq.gz")
  }


  /* ## 5. MIGs assembly */

  /* ### Input

     - filtered MIGs (`mig.R1`, `mig.R2`)
  */

  /* ### Output */

  /* - **5.1** MIG Consensus sequence */
  case object consensus {

    // A zipped folder with all consensus pairs one FASTQ file per pair:
    case object pairs {
      case object joined extends FileData("joined_consensus_pairs")("fastq")
      case object empty  extends FileData("empty_consensus_pairs")("fastq")
    }

    // Final per sample outputs:
    case object fasta extends FileData("consensus")("fasta")
    case object fastq extends FileData("consensus")("fastq")
  }

  /* - **5.2** MIG SPAdes contigs */
  case object scaffolds extends FileData("scaffolds")("fasta")


  /* ## 6. Annotation and clonotype definition and clustering */
  /* ## 7. Clonotype counting */

  /* ### Input

     - MIGs consensus sequences (output **5.1**)
  */

  /* ### Output */
  case object clonotype {
    case object totals extends FileData("clonotypes_totals")("tsv")

    /* - **6.1** CDR3 clonotype nucleotide sequence */
    case object CDR3 extends FileData("clonotype_cdr3")("fasta")

    /* - **6.2** IgBLAST results */
    case object igblastOut extends FileData("clonotypes_out")("txt")
    case object igblastTSV extends FileData("clonotypes_all")("tsv")
    case object igblastProductiveTSV  extends FileData("clonotype_productive")("tsv")
    case object igblastProductiveJSON extends FileData("clonotype_productive")("json")

    /* - **6.3** Clonotype-MIG table */
    case object migTable extends FileData("clonotype_mig")("tsv")

    /* - **6.4** Clonotype table */
    case object infoTable extends FileData("clonotype_info")("tsv")

    /* - **7.1** Clonotype counts table */
    // **: This is an extended version of the clonotype table (output **6.4**) which also contains the counts and frequency of each clonotype. It is a TSV table with the following structure:
    case object countsTable extends FileData("clonotype_counts")("tsv")
  }

  /* ## 8. Visualizations */

  /* ### Input

     - IgBLAST Productive Clonotypes TSV (output **6.2.3**)
  */

  /* ### Output */
  case object viz {
    /* - **8.1** Phylogenetic tree */
    case object phylogeneticTree extends FileData("phylogenetic_tree")("png")
  }
}
