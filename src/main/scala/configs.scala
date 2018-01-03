package era7bio.asdfjkl

import ohnosequences.loquat._
import ohnosequences.statika._, aws._
import ohnosequences.cosas._, types._, klists._, typeUnions._
import ohnosequences.awstools._, ec2._, autoscaling._, regions._
import ohnosequences.datasets._
import scala.concurrent.duration._

trait AnyStepConfig extends AnyLoquatConfig {

  val pipelineName: String
  val stepName: String
  lazy val loquatName: String = s"${pipelineName}-${stepName}"

  lazy val defaultAMI = AmazonLinuxAMI(Ireland, HVM, InstanceStore)

  lazy val managerConfig = ManagerConfig(
    defaultAMI,
    m3.medium,
    PurchaseModel.spot(0.1)
  )

  val size: Int

  lazy val workersConfig: AnyWorkersConfig = WorkersConfig(
    defaultAMI,
    m3.medium,
    PurchaseModel.spot(0.1),
    AutoScalingGroupSize(0, size, size)
  )
}

abstract class StepConfig(val stepName: String) extends AnyStepConfig

abstract class AnyUmiAnalysisConfig extends StepConfig("umi_analysis") {

  override lazy val amiEnv = amznAMIEnv(ami, javaHeap = 50, javaOptions = Seq("-XX:+UseG1GC"))
  override lazy val workersConfig: AnyWorkersConfig = WorkersConfig(
    defaultAMI,
    r3.`2xlarge`,
    PurchaseModel.spot(0.2),
    AutoScalingGroupSize(0, size, size)
  )

  override val sqsInitialTimeout = 6.hours
}

abstract class AnyAnnotationConfig extends StepConfig("igblast") {

  override lazy val amiEnv = amznAMIEnv(ami, javaHeap = 2)
  override lazy val workersConfig: AnyWorkersConfig = WorkersConfig(
    defaultAMI,
    // TODO: review instance type necessary for running IgBLAST
    c3.large,
    PurchaseModel.spot(0.04),
    AutoScalingGroupSize(0, size, size)
  )
}

abstract class AnyAllInOneConfig extends StepConfig("all_in_one") {

  override lazy val amiEnv = amznAMIEnv(ami, javaHeap = 50, javaOptions = Seq("-XX:+UseG1GC"))
  override lazy val workersConfig: AnyWorkersConfig = WorkersConfig(
    defaultAMI,
    r3.`2xlarge`,
    PurchaseModel.spot(0.2),
    AutoScalingGroupSize(0, size, size)
  )

  override val sqsInitialTimeout = 10.hours
}
