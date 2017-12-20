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
          TerminationDaemonBundle(
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
