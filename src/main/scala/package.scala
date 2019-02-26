package ohnosequences

import ohnosequences.cosas._, types._, records._, klists._
import ohnosequences.loquat._
import ohnosequences.fastarious._, SequenceQuality._, fastq._
import ohnosequences.joiner._, DNADistributions._

package object clonomapbridge {
  type ID       = String
  type SampleID = ID
  type StepName = String
  type DataMappings[DP <: AnyDataProcessingBundle] = List[DataMapping[DP]]
}
