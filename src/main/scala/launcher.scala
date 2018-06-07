package era7bio.asdfjkl

import ohnosequences.loquat._, utils._
import era7bio.asdfjkl.loquats._
import com.typesafe.scalalogging.LazyLogging
import scala.util.{ Try, Failure }
import scala.concurrent.duration._
import java.util.concurrent.ScheduledFuture
import java.nio.file.Files

case object launcher extends LazyLogging {

  def run(
    config: AnyLoquatConfig,
    user: LoquatUser,
    dataProcessing: AnyDataProcessingBundle,
    dataMappings: List[AnyDataMapping],
    manager: AnyManagerBundle,
    monitoringInterval: FiniteDuration
  ): Try[ScheduledFuture[_]] = {

    // Manager bundle needs some local directory to run in
    val localTmpDir = Files.createTempDirectory(config.loquatName).toFile

    LoquatOps.check(config, user, dataProcessing, dataMappings) match {
      case Left(msg) => Failure(new java.util.prefs.InvalidPreferencesFormatException(msg))
      case Right(aws) => {
        // executing a chain of steps that prepare AWS resources
        val resourcesPrepared: Try[_] =
          LoquatOps.prepareResourcesSteps(config, user, aws)
            .foldLeft[Try[_]](
              util.Success(true)
            ) { (result: Try[_], next: Step[_]) =>
              result.flatMap(_ => next.execute)
            }


        resourcesPrepared.flatMap { _ =>
          // if the resource are ready, launching manager locally
          resultToTry(
            manager.localInstructions(user).run(localTmpDir)
          )
        }.map { _ =>
          // and finally if everything went fine so far, returning a ScheduledFuture with the termination monitor
          new TerminationDaemonBundleWithExplicitCreationTime(
            config,
            Scheduler(1),
            dataMappings.length
          ).checkAndTerminate(
            after = 10.seconds,
            every = monitoringInterval
          )
        }
      }
    }
  }
}

// The TerminationDaemonBundle in loquat defines its managerCreationTime value
// querying the getCreatedTime method on the Option returned by
// aws.as.getGroup(<group-name>). This works only when there is an actual
// autoscaling group created. As webmiodx uses a local manager that is not
// running in a external autoscaling group but in the web server itself (see
// manager.localInstructions(user).run(localTmpDir) above), then the
// aws.as.getGroup method returns None and the managerCreationTime value of
// TerminationDaemonBundle in loquat is always None.
// In order to terminate the instance when a global timeout is reached, we need
// that the managerCreationTime value holds the actual creation time of the
// manager. We do that here by overriding the value to an explicit instance of
// Option[FiniteDuration], defined as the current time in millis.
class TerminationDaemonBundleWithExplicitCreationTime(
  override val config: AnyLoquatConfig,
  override val scheduler: Scheduler,
  override val initialCount: Int
) extends TerminationDaemonBundle(config, scheduler, initialCount) {

  override lazy val managerCreationTime = Some(System.currentTimeMillis.millis)

}
