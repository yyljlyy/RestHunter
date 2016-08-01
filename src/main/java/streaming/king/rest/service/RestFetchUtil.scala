package streaming.king.rest.service

import java.util

import net.csdn.common.path.Url
import org.apache.http.client.fluent.Request
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream
import streaming.core.strategy.platform.{PlatformManager, SparkStreamingRuntime}

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

/**
  * Created by xiaguobing on 2016/7/22.
  */
object RestFetchUtil {

  def urlRequest(requestMap: Map[String, Any]) = {
    val _keyPrefix = "metrics"
    val method: String = requestMap.getOrElse("method", "GET").asInstanceOf[String]
    val res = method match {
      case "POST" => Request.Post(new Url(requestMap("url").toString).toURI).bodyString(requestMap("param").toString, ContentType.APPLICATION_FORM_URLENCODED).execute()
      case _ => Request.Get(new Url(requestMap("url").toString).toURI).execute()
    }
    //val res = Request.Get(new Url(requestMap("url").toString).toURI).execute()
    val response = res.returnResponse()
    val content = EntityUtils.toString(response.getEntity)
    if (response != null && response.getStatusLine.getStatusCode == 200) {
      val keyWithPath = requestMap.filter(f => f._1.startsWith(_keyPrefix)).flatMap(f => f._2.asInstanceOf[String].split(",")).map { f => val arr = f.split(":"); (arr(0), arr(1)) }
      val newValue = keyWithPath.map { kPath =>
        val key = kPath._1
        val path = kPath._2
        val value = JSONPath.read(content, path).asInstanceOf[Any]
        (key, value)
      }
      Map[String, Any]() ++ requestMap ++ newValue ++ Map[String, Any]("request_done" -> true)
    } else {
      println(s" Rest API : ${requestMap("url")} fail. Reason:  " +
        s"${if (res == null || res.returnResponse() == null) "network error" else content}")
      Map[String, Any]()
    }
  }


  def nestedRestFetch(nestedResult: RDD[(String, Map[String, Any])]) = {
    val _keyPrefix = "key_"
    nestedResult.groupByKey().flatMap { f =>
      val m1: Iterable[Map[String, Any]] = f._2
      val listDone: List[Map[String, Any]] = m1.filter(_ ("request_done").asInstanceOf[Boolean]).toList
      val mPending: List[Map[String, Any]] = m1.filter(!_ ("request_done").asInstanceOf[Boolean]).toList
      if (listDone.size == 0 || mPending.size == 0) {
        m1.map(m1f => (f._1, m1f)) //全部已经执行的rest，就重新返回:全部未执行的rest，也重新返回
      } else {
        //val mDone: Map[String, Any] = listDone(0) //默认只有一个完成rest
        listDone.flatMap {
          mDone =>
            mPending.flatMap { p =>
              val url = p("url").asInstanceOf[String]
              val keyReplace: Map[String, Any] = mDone.filter(f1 => f1._1.startsWith(_keyPrefix) && url.contains("/:" + f1._1)) //找出要替换的参数
              (List(p) /: keyReplace) {
                (result, param) =>
                  result.flatMap {
                    rp =>
                      val resultUrl = rp("url").asInstanceOf[String]
                      param._2 match {
                        case arr: java.util.List[String] => {
                          arr.map { av =>
                            val newUrl: String = resultUrl.replaceAll(":" + param._1, av)
                            p ++ Map("url" -> newUrl, param._1 -> av) //更新url
                          }.filter(af => af.size > 0)
                        }
                        case _ => {
                          val newUrl: String = url.replaceAll(":" + param._1, param._2.toString)
                          List(p ++ Map("url" -> newUrl, param._1 -> param._2.toString)) //更新url
                        }
                      }
                  }
              }.map {
                lp =>
                  RestFetchUtil.urlRequest(lp)
              }.filter(lp => !(lp.isEmpty))
            }.map(p1 => (p1("id").asInstanceOf[String], p1))
        } ++ listDone.map(p2 => (p2("id").asInstanceOf[String], p2))
      }
    }
  }

  def urlTest(id: String, esService: ESService) = {
    val list = new util.ArrayList[Map[String, Any]]()
    urlTree(id, esService, list)
    val sparkContext: SparkContext = SparkStreamingRuntime.getOrCreate(PlatformManager.getRuntime.params).streamingContext.sparkContext
    val mrs = sparkContext.parallelize(list)
    val newMrs = mrs.filter(f => f("metastat") != "invalid").map { f =>
      val ref: String = f("metaref").asInstanceOf[String]
      if (ref == "-") {
        (f("id").asInstanceOf[String], RestFetchUtil.urlRequest(f))
      } else {
        (ref, f ++ Map("request_done" -> false))
      }
    }.filter(f => !(f._2.isEmpty))
    var fetchResult: RDD[(String, Map[String, Any])] = newMrs
    for (i <- 1 to 5) {
      //支持5层的嵌套
      fetchResult = nestedRestFetch(fetchResult)
    }
    val collect: Array[(String, Map[String, Any])] = fetchResult.collect()
    collect.map {
      f =>
        f._2.asJava
    }.toList.asJava
  }

  def urlTree(id: String, esService: ESService, list: java.util.List[Map[String, Any]]): Unit = {
    val result = esService.list(s"select * from monitor_db_rest where _id='${id}'")
    if (result.size == 1) {
      val map: Map[String, Any] = result(0)
      list.add(map)
      if (map("metaref") != "-") urlTree(map("metaref").asInstanceOf[String], esService, list)
    }
  }

}
