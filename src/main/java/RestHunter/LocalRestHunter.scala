package RestHunter

import org.apache.velocity.app.Velocity
import streaming.core.StreamingApp

/**
 * 5/25/16 WilliamZhu(allwefantasy@gmail.com)
 */
object LocalRestHunter {
  def main(args: Array[String]): Unit = {
    Velocity.addProperty("eventhandler.include.class", "org.apache.velocity.app.event.implement.IncludeRelativePath")
    Velocity.addProperty("velocimacro.library.autoreload", true)
    StreamingApp.main(Array(
      "-streaming.master", "local[2]",
      "-streaming.duration", "120",
      "-streaming.name", "god",
      "-streaming.rest", "true"

    ))
  }
}
