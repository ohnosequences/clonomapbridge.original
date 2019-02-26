package ohnosequences.clonomapbridge

import ohnosequences.clonomapbridge._, data._, loquats._
import ohnosequences.loquat._
import ohnosequences.statika._, aws._
import ohnosequences.datasets._
import ohnosequences.awstools._, s3._
import com.amazonaws.auth._, profile._

/**
 * Abstract wrapper containing all the configuration for the whole pipeline
 */
trait AnyPipeline { pipeline =>

  // These must be defined by the class implementing this trait
  val inputPairedReads: Map[SampleID, (S3Resource, S3Resource)]
  val outputS3Folder: (SampleID, StepName) => S3Folder

  val metadata: AnyArtifactMetadata
  val iamRoleName: String
  val logsS3Prefix: S3Folder

  lazy val fullName: String = this.getClass.getName.split("\\$").mkString(".")
  lazy val name: String = fullName.split('.').last.toLowerCase

  /* This trait helps to set these common values */
  trait CommonConfigDefaults extends AnyLoquatConfig {

    val pipelineName: String          = pipeline.name
    val metadata: AnyArtifactMetadata = pipeline.metadata
    val iamRoleName: String           = pipeline.iamRoleName
    val logsS3Prefix: S3Folder        = pipeline.logsS3Prefix
  }

  /** Steps configurations. The size is the number of samples to analyze */
  case class UmiAnalysisConfig(val size: Int)
    extends AnyUmiAnalysisConfig with CommonConfigDefaults
  case class AnnotationConfig(val size: Int)
    extends AnyAnnotationConfig with CommonConfigDefaults
  // ...
  case class AllInOneConfig(val size: Int)
    extends AnyAllInOneConfig with CommonConfigDefaults

  /* You have to set these values to customize configuration for each step */
  val umiAnalysisConfig: AnyUmiAnalysisConfig
  val annotationConfig: AnyAnnotationConfig
  // ...
  val allInOneConfig: AnyAllInOneConfig

  // Boilerplate definitions that are derived from the ones above:
  private def outputFor(
    sampleId: SampleID,
    stepConfig: AnyStepConfig
    (d: AnyData): (AnyData, S3Resource) = {
    d -> S3Resource(
      outputS3Folder(sampleId, stepConfig.stepName) / s"${sampleId}_${d.label}"
    )
  }

  lazy val umiAnalysisDataMappings
  : DataMappings[umiAnalysis.dataProcessing.type] =
    inputPairedReads.toList.map { case (sampleId, (r1, r2)) =>
      DataMapping(sampleId, umiAnalysis.dataProcessing)(
        remoteInput = Map(
          data.r1 -> r1,
          data.r2 -> r2
        ),
        remoteOutput = umiAnalysis.outputData
          .keys.types.asList
          .map(outputFor(sampleId, umiAnalysisConfig)).toMap
      )
    }

  lazy val annotationDataMappings: DataMappings[igblastAnnotation.TRB.type] =
    inputPairedReads.toList.map { case (sampleId, (r1, r2)) =>
      DataMapping(sampleId, igblastAnnotation.TRB)(
        remoteInput = Seq(
          data.consensus.fasta
        ).map(outputFor(sampleId, umiAnalysisConfig)).toMap,
        remoteOutput = igblastAnnotation.outputData
          .keys.types.asList
          .map(outputFor(sampleId, annotationConfig)).toMap
      )
    }
  // ...

  lazy val allInOneDataMappings: DataMappings[allInOne.dataProcessing.type] =
    inputPairedReads.toList.map { case (sampleId, (r1, r2)) =>
      DataMapping(sampleId, allInOne.dataProcessing)(
        remoteInput = Map(
          data.r1 -> r1,
          data.r2 -> r2
        ),
        remoteOutput =
          umiAnalysis.outputData
            .keys.types.asList
            .map(outputFor(sampleId, umiAnalysisConfig)).toMap ++
          igblastAnnotation.outputData
            .keys.types.asList
            .map(outputFor(sampleId, annotationConfig)).toMap
      )
    }

  trait FixedName extends AnyLoquat {
    override lazy val fullName: String =
      s"${pipeline.fullName}.${this.toString}"
  }

  case object umiAnalysisStep extends Loquat(
    umiAnalysisConfig,
    umiAnalysis.dataProcessing
  )(umiAnalysisDataMappings) with FixedName

  case object annotationStep  extends Loquat(
    annotationConfig,
    igblastAnnotation.TRB
  )(annotationDataMappings) with FixedName

  // ...

  case object allInOneStep  extends Loquat(
    allInOneConfig,
    allInOne.dataProcessing
  )(allInOneDataMappings) with FixedName
}
