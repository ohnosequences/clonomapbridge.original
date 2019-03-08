package ohnosequences.clonomapbridge

import ohnosequences.repseqmiodx._
import ohnosequences.clonomapbridge._, loquats._, data._
import ohnosequences.loquat._
import ohnosequences.datasets._
import ohnosequences.statika._, aws._
import ohnosequences.awstools._, ec2._ , s3._, autoscaling._, regions._
import ohnosequences.db.tcr.{Species, Chain}
import com.amazonaws.auth.profile._
import com.amazonaws.auth._
import scala.concurrent.duration._
import scala.util.Try
import java.net.URL

// Common case class for the input data
case class InputData(
  r1 : S3Object,
  r2 : S3Object,
  species: Species,
  chain: Chain
)

// Common case class for the output data, which is parametrized by a base
// directory and an analysis identifier (which comes from the webapp)
case class OutputData(
  baseFolder : S3Folder,
  analysisID : ID
) {
  /**
   * Define the S3 directory where the output will be uploaded
   */
  def outputS3Folder: StepName =>
  AnyData => (AnyData, S3Resource) =
    step => data =>
      (
        data,
        S3Resource(
          baseFolder / analysisID / step / s"${analysisID}_${data.label}"
        )
      )

  /**
   * Map the output data with their corresponding S3 paths
   */
  def remoteOutput : () => Map[AnyData, S3Resource] =
    () =>
      umiAnalysis.outputData
        .keys.types.asList
          .map( outputS3Folder("umi-analysis") )
          .toMap ++
      igblastAnnotation.outputData
        .keys.types.asList
        .map( outputS3Folder("annotation") )
          .toMap ++
      visualizations.outputData
        .keys.types.asList
        .map( outputS3Folder("visualizations") )
          .toMap
}

/**
 * This is called from the web application, and it contains the implementation
 * of all the loquat configuration and running
 */
case object analysis {

  // S3 client for downloading/uloading files
  lazy val S3 =
    s3.defaultClient

  type Email =
    String

  type ID =
    String

  /**
   * This is the one and only method you need from here.
   *
   * It receives an InputData instance, an OutputData instance, an email that
   * identifies the loquat user and the output is a list of (outputname,
   * s3address)
   */
  def runAnalysis: InputData => OutputData => Email =>
  Map[AnyData, S3Resource] =
    input => output => email => {

      // Create a configuration adding a timestamp to the loquat name in order
      // to create unique loquat names, which allows several instances of the
      // same type to be launched at the same time.
      val timestamp : Long = System.currentTimeMillis
      val loquatConf = impl(timestamp)

      val dms = List( (loquatConf.dataMapping(input)(output)) )

      val fut =
        launcher.run(
          config             = loquatConf.defaultConfig      ,
          user               = loquatConf.loquatUser(email)  ,
          dataProcessing     = loquatConf.analysisBundle     ,
          dataMappings       = dms                           ,
          manager            = loquatConf.managerBundle(dms) ,
          monitoringInterval = 10.minute
        )

      output.remoteOutput()
    }

  // WARNING: The code inside this case class is hacky.
  //
  // The timestamp parameter is a suffix added to the loquat name: this allows
  // several instances of the same type to be launched at the same time just by
  // giving them different unique names.
  //
  // If you change either the name of this case class or the name *or type* of
  // its parameter, you should also change the three `fullName` values you will
  // find inside:
  //   * the ManagerBundle object created from managerBundle method,
  //   * the worker object,
  //   * the workerCompat object.
  // These `fullName` values contain references that you should take into
  // account: the `impl` name, hardcoded inside the `fullName` string; the
  // `timestamp` parameter, passed as a value to the interpolated `fullName`
  // string; and the "L" after the "${timestamp}" substring, that is needed
  // only because `timestamp` is a `Long`.
  //
  // The reason of this hack: Loquat (mainly Statika) was designed to treat the
  // code as configuration, so any change to the configuration should be done
  // through modifications in the code. We *do* need to change the name of the
  // loquat in runtime (to be able to launch more than one instance at the same
  // time), so we need this hacky hack in order to bypass the loquat
  // expectations.
  case class impl(val timestamp: Long) {

    type Namespace =
      String

    def analysisJarMetadata =
      ohnosequences.generated.metadata.clonomapbridge

    type AnalysisBundle =
      allInOne.dataProcessing.type

    type DataMappings =
      List[DataMapping[AnalysisBundle]]

    def analysisBundle: AnalysisBundle =
      allInOne.dataProcessing

    def dataMapping: InputData => OutputData => DataMapping[AnalysisBundle] =
      input => output =>
        DataMapping(output.analysisID, allInOne.dataProcessing)(
          remoteInput =
            Map(
              data.r1 -> MessageResource(input.r1.toString),
              data.r2 -> MessageResource(input.r2.toString),
              species -> MessageResource(input.species.toString),
              chain   -> MessageResource(input.chain.toString)
            )
          ,
          remoteOutput = output.remoteOutput()
        )

    def managerBundle
    : List[DataMapping[AnalysisBundle]] => AnyManagerBundle =
      dms =>
        new ManagerBundle(worker)(dms) {
          override
          lazy val fullName: String =
            s"ohnosequences.clonomapbridge.analysis.impl(${timestamp}L)"
        }

    def defaultAMI =
      AmazonLinuxAMI(Ireland, HVM, InstanceStore)

    object DefaultManagerConfig
      extends ManagerConfig(
        defaultAMI,
        m3.medium,
        PurchaseModel.spot(0.1)
      )

    case class AnalysisConfig(
      val loquatName    : String            ,
      val logsS3Prefix  : S3Folder          ,
      val managerConfig : AnyManagerConfig
    )
    extends AnyLoquatConfig {

      val metadata =
        analysisJarMetadata

      // TODO create a specific role
      val iamRoleName =
        "era7-projects"

      override
      lazy val amiEnv =
        amznAMIEnv(
          ami,
          javaHeap    = 50, // GB
          javaOptions = Seq("-XX:+UseG1GC")
        )

      override
      lazy val workersConfig: AnyWorkersConfig =
        WorkersConfig(
          defaultAMI,
          r3.`2xlarge`,
          PurchaseModel.spot(0.2),
          AutoScalingGroupSize(0, 1, 1)
        )

      override
      val sqsInitialTimeout =
        10.hours

      override
      val terminationConfig = TerminationConfig(
        // if true loquat will terminate after solving all initial tasks
        terminateAfterInitialDataMappings = true,
        // if true loquat will terminate after errorQueue will contain more
        // unique messages than threshold
        errorsThreshold = None,
         // maximum time for processing one task
        taskProcessingTimeout = None,
        // maximum time for everything
        globalTimeout = Some(6.hours)
      )
    }

    val defaultConfig =
      AnalysisConfig(
        loquatName    = s"data-analysis-$timestamp",
        logsS3Prefix  = S3Folder("miodx", "clonomap")/"analysis"/"log"/,
        managerConfig = DefaultManagerConfig
      )

    // worker
    //////////////////////////////////////////////////////////////////////////////
    case object worker extends WorkerBundle(
      analysisBundle,
      defaultConfig
    ) {

      lazy val fullName: String =
        s"ohnosequences.clonomapbridge.analysis.impl(${timestamp}L).worker"
    }

    case object workerCompat extends CompatibleWithPrefix("ohnosequences.clonomapbridge.analysis.impl")(
      environment = defaultConfig.amiEnv,
      bundle      = worker,
      metadata    = defaultConfig.metadata
    ) {
      override lazy val fullName: String =
        s"ohnosequences.clonomapbridge.analysis.impl(${timestamp}L).workerCompat"
    }

    //////////////////////////////////////////////////////////////////////////////

    def loquatUser: Email => LoquatUser =
      email =>
        LoquatUser(
          email             = email,
          localCredentials  =
            new AWSCredentialsProviderChain(
              new InstanceProfileCredentialsProvider(false),
              new ProfileCredentialsProvider("default")
            ),
          keypairName       = "miodx-dev"
        )

    val me = LoquatUser(
      email             = "eparejatobes@ohnosequences.com",
      localCredentials  =
        new AWSCredentialsProviderChain(
          new InstanceProfileCredentialsProvider(false),
          new ProfileCredentialsProvider("default")
        ),
      keypairName       = "miodx-dev"
    )
  }
}
