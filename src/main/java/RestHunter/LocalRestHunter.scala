package RestHunter

import org.apache.velocity.app.Velocity
import streaming.core.StreamingApp

/**
  * 5/25/16 WilliamZhu(allwefantasy@gmail.com)
  */
object LocalRestHunter {
  def main(args: Array[String]): Unit = {
    val newArgs = Array(
      "-streaming.duration", "30",
      "-streaming.name", "NestRestMonitor",
      "-streaming.jobs", "NestRestMonitor",
      "-streaming.rest", "true"
    ) ++ args
    StreamingApp.main(newArgs)
  }
}
