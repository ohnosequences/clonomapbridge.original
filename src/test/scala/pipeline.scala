package ohnosequences.clonomapbridge.test

import ohnosequences.clonomapbridge._
import era7bio.repseqmiodx._
import ohnosequences.clonomapbridge.test.testDefaults._
import ohnosequences.loquat._
import ohnosequences.statika._, aws._
import ohnosequences.awstools._, s3._
import ohnosequences.datasets._

case object pipeline extends AnyPipeline with TestPipelineDefaults {
  // override lazy val name = "test-pipeline"

  val sampleIDs: Set[SampleID] = Set(
    "1605_S6",
    "2904_S2",
    "2905_S3",
    "3301_S1",
    "P2D21_S5",
    "P2D6_S4"
  )

  val testDataPrefix = s3"era7p/repseqmiodx/data/in/test-data" / "19Apr" /

  val inputPairedReads: Map[SampleID, (S3Resource, S3Resource)] = sampleIDs.map { id =>
    id -> ((
      S3Resource(testDataPrefix / s"${id}_L001_R1_001.fastq.gz"),
      S3Resource(testDataPrefix / s"${id}_L001_R2_001.fastq.gz")
    ))
  }.toMap

}
