package ohnosequences.clonomapbridge.test

import ohnosequences.clonomapbridge._
import ohnosequences.repseqmiodx._
import ohnosequences.loquat._
import ohnosequences.statika._, aws._
import ohnosequences.awstools._, ec2._ , s3._, autoscaling._, regions._
import com.amazonaws.auth.profile._

case object testDefaults {

  lazy val metadata = ohnosequences.generated.metadata.clonomapbridge

  /* Output test data is scoped by version */
  lazy val outputS3Prefix = s3"resources.ohnosequences.com" /
    metadata.organization /
    metadata.artifact /
    "test-all-in-one" /

  def outputS3FolderFor(pipeline: String): (SampleID, StepName) => S3Folder = { (sampleID, step) =>
    outputS3Prefix / sampleID /
  }


  trait TestPipelineDefaults extends AnyPipeline {

    val metadata = testDefaults.metadata
    val iamRoleName = "era7-projects"
    lazy val logsS3Prefix = s3"era7-projects-loquats" / "repseqmiodx" / name /
    lazy val outputS3Folder = testDefaults.outputS3FolderFor(name)

    lazy val umiAnalysisConfig = UmiAnalysisConfig(inputPairedReads.keys.size)
    lazy val annotationConfig = AnnotationConfig(inputPairedReads.keys.size)
    lazy val allInOneConfig = AllInOneConfig(inputPairedReads.keys.size)
  }

  lazy val alexey = LoquatUser(
    email = "aalekhin@ohnosequences.com",
    localCredentials = new ProfileCredentialsProvider("default"),
    keypairName = "aalekhin"
  )

  lazy val edu = LoquatUser(
    email = "eparejatobes@ohnosequences.com",
    localCredentials = new ProfileCredentialsProvider("default"),
    keypairName = "era7bioinformatics.dev.eduardopt"
  )
}
