package streaming.king.rest.transform

import java.util

import org.apache.log4j.Logger
import org.apache.spark.streaming.dstream.DStream
import serviceframework.dispatcher.{Compositor, Processor, Strategy}
import streaming.core.compositor.spark.streaming.CompositorHelper
import streaming.king.rest.service.{JSONPath, JSONPathExtractor}

import scala.collection.JavaConversions._


/**
 * 5/25/16 WilliamZhu(allwefantasy@gmail.com)
 */
class JSonExtractCompositor[T] extends Compositor[T] with CompositorHelper {
  private var _configParams: util.List[util.Map[Any, Any]] = _

  val logger = Logger.getLogger(classOf[JSonExtractCompositor[T]])

  override def initialize(typeFilters: util.List[String], configParams: util.List[util.Map[Any, Any]]): Unit = {
    this._configParams = configParams
  }

  def keyPrefix = {
    config("keyPrefix", _configParams).getOrElse("key_")
  }

  def resultKey = {
    config("resultKey", _configParams).getOrElse("result")
  }

  def flat = {
    config("flat", _configParams).getOrElse("true").toBoolean
  }

  override def result(alg: util.List[Processor[T]], ref: util.List[Strategy[T]], middleResult: util.List[T], params: util.Map[Any, Any]): util.List[T] = {
    val mrs = middleResult(0).asInstanceOf[DStream[Map[String, AnyRef]]]
    val _keyPrefix = keyPrefix
    val _resultKey = resultKey
    val _flat = flat

    val newMrs = mrs.flatMap { f =>
      //var keyWithPath = f.filter(f => f._1.startsWith(_keyPrefix)).map(f => (f._1, f._2.asInstanceOf[String])).toMap
      val keyWithPath = f.filter(f => f._1.startsWith(_keyPrefix)).flatMap(f => f._2.asInstanceOf[String].split(",")).map{f=>val arr=f.split(":");(arr(0),arr(1))}

      val json = f(_resultKey).toString

      val newValue = keyWithPath.map { kPath =>
        val key = kPath._1
        val path = kPath._2
        val value = JSONPath.read(json, path).asInstanceOf[Any]
        (key, value)
      }

      if (_flat) {
        newValue.map { k =>
          f + (k._1 -> k._2)
        }

      } else {
        List(f ++ newValue)
      }

    }
    List(newMrs.asInstanceOf[T])
  }


}

object Test {

  def main(args: Array[String]): Unit = {
    import net.liftweb.json._
    val akg = parse( s""" {
                        | "took": 2,
                        | "timed_out": false,
                        | "_shards": {
                        |     "total": 5,
                        |     "successful": 5,
                        |     "failed": 0
                        | },
                        | "hits": {
                        |     "total": 1,
                        |     "max_score": 1,
                        |     "hits": [
                        |         {
                        |             "_index": "monitor_db_rest",
                        |             "_type": "rest",
                        |             "_id": "1",
                        |             "_score": 1,
                        |             "_timestamp": 1464154284797,
                        |             "_source": {
                        |                 "ip": "10.148.16.101",
                        |                 "logtype": "ESAPI",
                        |                 "appname": "letv-online-analysis",
                        |                 "url": "http://10.148.16.101:9200/_cat/count?format=json",
                        |                 "method": "GET",
                        |                 "key_bulk_count": "count"
                        |             }
                        |         },
                        |         {
                        |             "_index": "monitor_db_rest",
                        |             "_type": "rest",
                        |             "_id": "1",
                        |             "_score": 1,
                        |             "_timestamp": 1464154284797,
                        |             "_source": {
                        |                 "ip": "10.148.16.101",
                        |                 "logtype": "ESAPI",
                        |                 "appname": "letv-online-analysis",
                        |                 "url": "http://10.148.16.101:9200/_cat/count?format=json",
                        |                 "method": "GET",
                        |                 "key_bulk_count": "count"
                        |             }
                        |         }
                        |     ]
                        | }
                        |} """.stripMargin)

    println((akg \ "hits" \ "hits")(0) \ "_source" \ "method")

    println(JSONPathExtractor.findPath(akg, "(hits.hits)(0)._source.method"))


  }
}
