package streaming.king.rest.service

import com.google.inject.{Inject, Singleton}
import net.csdn.common.collections.WowCollections
import net.csdn.common.path.Url
import net.csdn.common.settings.Settings
import net.csdn.modules.http.RestRequest
import net.csdn.modules.transport.HttpTransportService
import net.sf.json.{JSONArray, JSONObject}
import serviceframework.dispatcher.StrategyDispatcher
import streaming.core.strategy.platform.PlatformManager

import scala.collection.JavaConversions._

/**
 * 5/25/16 WilliamZhu(allwefantasy@gmail.com)
 */
@Singleton
class ESService @Inject()(settings: Settings,
                          transportService: HttpTransportService
                           ) extends JSONHelper {

  def platformManager = PlatformManager.getOrCreate

  def dispatcher = StrategyDispatcher.getOrCreate(null)

  def runtime(name: String) = PlatformManager.getRuntime(name, new java.util.HashMap[Any, Any]())

  val hostAndPort = settings.get("es.nodes").split(",").head
  val resource = settings.get("es.resource", "monitor_db_rest/rest")
  val queryUrl = new Url(s"http://${hostAndPort}/_sql")
  val saveUrl = new Url(s"http://${hostAndPort}/${resource}")

  def list(sql: String) = {

    val response = transportService.get(queryUrl, WowCollections.map("sql", sql).asInstanceOf[java.util.Map[String, String]])
    import net.liftweb.json._
    implicit val formats = net.liftweb.json.DefaultFormats
    val json = parse(response.getContent)
    val result = json \ "hits" \ "hits"
    JSONArray.fromObject(toJsonStr(result))
  }

  def listWithPage(sql: String, from: Long, size: Long) = {
    val response = transportService.get(queryUrl, WowCollections.map("sql", s"${sql} limit ${from},${size}").asInstanceOf[java.util.Map[String, String]])
    import net.liftweb.json._
    implicit val formats = net.liftweb.json.DefaultFormats
    val json = parse(response.getContent)
    val result = json \ "hits" \ "hits"
    val total =  json \ "hits" \ "total"
    (JSONArray.fromObject(toJsonStr(result)),toJsonStr(total).toLong)
  }

  def jsave(item: java.util.Map[String, String]) = {
    val id=item("id").asInstanceOf[String]
    val url = new Url(s"${saveUrl}/$id")
    val response = transportService.http(url, toJsonMap4J(item), RestRequest.Method.PUT)
    response != null && response.getStatus == 200
  }


  def jdelete(id: String) = {
    val url = new Url(s"${saveUrl}/$id")
    val response = transportService.http(url, null, RestRequest.Method.DELETE)
    response != null && response.getStatus == 200
  }


}

trait JSONHelper {

  import net.liftweb.{json => SJSon}

  def parseJson[T](str: String)(implicit m: Manifest[T]) = {
    implicit val formats = SJSon.DefaultFormats
    SJSon.parse(str).extract[T]
  }

  def toJsonStr(item: AnyRef) = {
    implicit val formats = SJSon.Serialization.formats(SJSon.NoTypeHints)
    SJSon.Serialization.write(item)
  }

  def toJsonList4J(item: Object) = {
    JSONArray.fromObject(item).toString
  }

  def toJsonMap4J(item: Object) = {
    JSONObject.fromObject(item).toString
  }
}
