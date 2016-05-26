package streaming.king.rest

import com.google.inject.Inject
import net.csdn.annotation.rest.At
import net.csdn.common.collections.WowCollections
import net.csdn.modules.http.ApplicationController
import net.csdn.modules.http.RestRequest.Method._
import serviceframework.dispatcher.StrategyDispatcher
import streaming.core.strategy.platform.PlatformManager
import streaming.king.rest.service.{ESService, Paginate}

/**
 * 5/25/16 WilliamZhu(allwefantasy@gmail.com)
 */
class MetaController @Inject()(esService: ESService) extends ApplicationController {

  @At(path = Array("/resthunter/list"), types = Array(GET))
  def list = {

    cPaginate
    val items = esService.list("select * from monitor_db_rest")
    paginate.totalItems(items.size)
    renderHtml(200, "/rest/index.vm", WowCollections.map(
      "template", "/rest/items.vm", "feeds", items
    ))
  }

  @At(path = Array("/resthunter/create"), types = Array(POST))
  def postCreate = {
    esService.jsave(params())
    redirectTo("/resthunter/list", WowCollections.map())
  }

  @At(path = Array("/resthunter/create"), types = Array(GET))
  def create = {
    renderHtmlWithMaster(200, "/rest/form_master.vm", WowCollections.map(
      "template", "/rest/createItem.vm"
    ))
  }

  @At(path = Array("/resthunter/delete"), types = Array(GET))
  def delete = {
    esService.jdelete(param("id"))
    redirectTo("/resthunter/list", WowCollections.map())
  }

  def cPaginate = {
    paginate = new Paginate(paramAsInt("page", 1), paramAsInt("pageSize", 15))
  }

  var paginate: Paginate = _


}
