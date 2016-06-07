package streaming.king.rest

import java.util

import com.google.inject.Inject
import com.jayway.jsonpath.JsonPath
import net.csdn.annotation.rest.At
import net.csdn.common.collections.WowCollections
import net.csdn.common.path.Url
import net.csdn.modules.http.{ApplicationController, ViewType}
import net.csdn.modules.http.RestRequest.Method._
import net.sf.json.{JSONArray, JSONObject}
import org.apache.http.client.fluent.Request
import streaming.king.rest.service.{ESService, JSONPath, JSONPathTester, Paginate}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable
/**
 * 5/25/16 WilliamZhu(allwefantasy@gmail.com)
 */
class MetaController @Inject()(esService: ESService) extends ApplicationController {

  @At(path = Array("/resthunter/list"), types = Array(GET))
  def list = {

    cPaginate
    val items = esService.list("select * from monitor_db_rest")
    paginate.totalItems(items.size)
    renderHtml(200, "/rest/ace.vm", WowCollections.map(
       "feeds", items
    ))
  }

  @At(path = Array("/resthunter/metalist"), types = Array(GET))
  def metalist = {
    val page= paramAsLong("page",1)
    val rows= paramAsLong("rows",10)
    var sidx= param("sidx","appname")
    if(sidx=="") sidx="appname"
    val sort= param("sord","")
    val from=rows*(page-1)
    val items = esService.listWithPage(s"select * from monitor_db_rest order by ${sidx} ${sort}",from,rows)
    val list =extractSourceMap(items._1)
    val mapResponse: java.util.Map[String, Any] = Map("rows"->list.map(_.asJava).asJava,"page"->page,"records"->items._2,"total"->Math.ceil(items._2/rows.toDouble).toInt,"userdata"->Map().asJava).asJava
    render(200, mapResponse, ViewType.json)
  }

  @At(path = Array("/resthunter/test"), types = Array(GET))
  def metaTest = {
    val id=param("id","");
    if(id==""){
      render(200, "ID is null" ,ViewType.json)
    }else{
      val items = esService.list(s"select * from monitor_db_rest where _id='${id}'")
      val list: List[Map[_, Any]] = extractSourceMap(items).toList
      val parseResult: List[Map[String, Any]] = JSONPathTester.parse(list.asInstanceOf[List[Map[String,Any]]])
      render(200, parseResult.map(_.asJava).asJava ,ViewType.json)
    }


  }

  @At(path = Array("/resthunter/create"), types = Array(POST))
  def postCreate = {
    esService.jsave(params())
    //redirectTo("/resthunter/list", WowCollections.map())
    render(200, "ok" ,ViewType.json)
  }

  @At(path = Array("/resthunter/delete"), types = Array(GET,POST,DELETE))
  def delete = {
    esService.jdelete(param("id"))
    //redirectTo("/resthunter/list", WowCollections.map())
    render(200, "ok" ,ViewType.json)
  }

  @At(path = Array("/resthunter/path/validate"), types = Array(GET))
  def pathValidate = {
    val url = param("url")
    val path = param("path")
    val res = Request.Get(new Url(url).toURI).execute().returnContent().asString()
    val value = JSONPath.read(res, path).toString
    render(200, value)
  }

  def extractSourceMap(items:JSONArray)={
    items.map{
      f=>
        val json: JSONObject = f.asInstanceOf[JSONObject]
        val source =json.getJSONObject("_source")
        source.toMap
        //Map("id"->json.getString("_id"),"logtype"->source.getString("logtype"),"appname"->source.getString("appname"),"apptype"->source.getString("apptype"),"url"->source.getString("url"),"metrics"->source.optString("metrics","")).asJava
    }
  }

  def cPaginate = {
    paginate = new Paginate(paramAsInt("page", 1), paramAsInt("pageSize", 15))
  }

  var paginate: Paginate = _


}