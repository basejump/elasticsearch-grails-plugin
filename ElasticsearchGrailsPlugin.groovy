import org.grails.plugins.elasticsearch.ElasticSearchInterceptor
import static org.grails.plugins.elasticsearch.ElasticSearchHelper.*
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import static org.elasticsearch.search.builder.SearchSourceBuilder.*
import static org.elasticsearch.client.Requests.*
import static org.elasticsearch.index.query.xcontent.QueryBuilders.*
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.search.SearchHit
import org.springframework.beans.SimpleTypeConverter
import org.springframework.beans.factory.FactoryBean
import static org.elasticsearch.groovy.node.GNodeBuilder.*
import org.grails.plugins.elasticsearch.ElasticSearchHelper
import org.springframework.context.ApplicationContext
import org.elasticsearch.groovy.client.GClient
import static org.elasticsearch.index.query.xcontent.QueryBuilders.termQuery
import grails.converters.JSON

class ElasticsearchGrailsPlugin {
  // the plugin version
  def version = "0.1"
  // the version or versions of Grails the plugin is designed for
  def grailsVersion = "1.3 > *"
  // the other plugins this plugin depends on
  def dependsOn = [services: "1.3 > *"]
  def loadAfter = ['services']
  // resources that are excluded from plugin packaging
  def pluginExcludes = [
          "grails-app/views/error.gsp"
  ]

  // TODO Fill in these fields
  def author = "Manuarii Stein"
  def authorEmail = "mstein@doc4web.com"
  def title = "ElasticSearch Plugin"
  def description = '''\\
Integrates ElasticSearch with Grails, allowing to index domain instances or raw data.
Based on Graeme Rocher spike.
'''

  // URL to the plugin's documentation
  def documentation = "http://grails.org/plugin/elasticsearch"

  def doWithWebDescriptor = { xml ->
    // TODO Implement additions to web.xml (optional), this event occurs before
  }

  def doWithSpring = {
    entityInterceptor(ElasticSearchInterceptor) {
      elasticSearchIndexService = ref("elasticSearchIndexService")
    }
    elasticSearchNode(ClientNodeFactoryBean)
    elasticSearchHelper(ElasticSearchHelper) {
      elasticSearchNode = ref("elasticSearchNode")
    }
  }

  def onShutdown = { event ->
    event.ctx.getBean("elasticSearchNode").close()
  }

  def doWithDynamicMethods = { ctx ->
    def helper = ctx.getBean(ElasticSearchHelper)

    for (GrailsDomainClass domain in application.domainClasses) {
      if (domain.getPropertyValue("searchable")) {
        def domainCopy = domain
        domain.metaClass.static.search = { String q, Map params = [from: 0, size: 60, explain: true] ->
          helper.withElasticSearch { GClient client ->
            try {
              /*def response = client.search {
                indices(domainCopy.packageName ?: domainCopy.propertyName)
                types domainCopy.propertyName
                source {
                  query {
                    element(term:queryString(q))
                  }
                  *//*element(query:queryString(q))*//*
                }
                *//*from(params.from ?: 0)
                size(params.size ?: 0)
                explain(params.containsKey('explain') ? params.explain : true)*//*
              }.actionGet()*/
              def response = client.search(
                      searchRequest(domainCopy.packageName ?: domainCopy.propertyName)
                              .searchType(SearchType.DFS_QUERY_THEN_FETCH)
                              .source(searchSource().query(queryString(q))
                              .from(params.from ?: 0)
                              .size(params.size ?: 60)
                              .explain(params.containsKey('explain') ? params.explain : true))

              ).actionGet()
              def searchHits = response.hits()
              def result = [:]
              result.total = searchHits.totalHits()

              println "Found ${result.total ?: 0} result(s)."
              def typeConverter = new SimpleTypeConverter()

              // Convert the hits back to their initial type
              result.searchResults = searchHits.hits().collect { SearchHit hit ->
                def identifier = domainCopy.getIdentifier()
                def id = typeConverter.convertIfNecessary(hit.id(), identifier.getType())
                def instance = domainCopy.newInstance()
                instance."${identifier.name}" = id
                instance.properties = hit.source
                println "instance.properties : ${hit.source}"
                if(hit.source.user) {
                  println "> : ${instance.user} : ${hit.source.user.class}"
                }
                return instance
              }
              return result
            } catch (e) {
              e.printStackTrace()
              return [searchResult: [], total: 0]
            }
          }
        }
      }
    }
  }

  def doWithApplicationContext = { applicationContext ->
    // TODO Implement post initialization spring config (optional)
  }

  def onChange = { event ->
    // TODO Implement code that is executed when any artefact that this plugin is
    // watching is modified and reloaded. The event contains: event.source,
    // event.application, event.manager, event.ctx, and event.plugin.
  }

  def onConfigChange = { event ->
    // TODO Implement code that is executed when the project configuration changes.
    // The event is the same as for 'onChange'.
  }
}

class ClientNodeFactoryBean implements FactoryBean {

  Object getObject() {
    org.elasticsearch.groovy.node.GNodeBuilder nb = nodeBuilder()
    nb.settings{
      node {
        client=true
      }
    }
    nb.node()
  }

  Class getObjectType() {
    return org.elasticsearch.groovy.node.GNode
  }

  boolean isSingleton() {
    return true
  }
}