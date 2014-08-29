/*
 * Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics
package snowplow
package enrich
package common
package enrichments
package registry

// Java
import java.net.URI

// Scala
import scala.collection.JavaConversions._
import scala.reflect.BeanProperty

// Utils
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder

// Maven Artifact
import org.apache.maven.artifact.versioning.DefaultArtifactVersion

// Scalaz
import scalaz._
import Scalaz._

// json4s
import org.json4s.JValue

// Iglu
import iglu.client.SchemaKey
import iglu.client.validation.ProcessingMessageMethods._

// This project
import utils.{ConversionUtils => CU}
import utils.MapTransformer
import utils.MapTransformer._
import utils.ScalazJson4sUtils

/**
 * Companion object. Lets us create a
 * CampaignsEnrichment from a JValue
 */
object CampaignsEnrichment extends ParseableEnrichment {

  val supportedSchemaKey = SchemaKey("com.snowplowanalytics.snowplow", "campaigns", "jsonschema", "1-0-0")

  /**
   * Creates a CampaignsEnrichment instance from a JValue.
   * 
   * @param config The referer_parser enrichment JSON
   * @param schemaKey The SchemaKey provided for the enrichment
   *        Must be a supported SchemaKey for this enrichment   
   * @return a configured CampaignsEnrichment instance
   */
  def parse(config: JValue, schemaKey: SchemaKey): ValidatedNelMessage[CampaignsEnrichment] = {
    isParseable(config, schemaKey).flatMap( conf => {
      (for {
        medium    <- ScalazJson4sUtils.extract[List[String]](config, "parameters", "fields", "mktMedium")
        source    <- ScalazJson4sUtils.extract[List[String]](config, "parameters", "fields", "mktSource")
        term      <- ScalazJson4sUtils.extract[List[String]](config, "parameters", "fields", "mktTerm")
        content   <- ScalazJson4sUtils.extract[List[String]](config, "parameters", "fields", "mktContent")
        campaign  <- ScalazJson4sUtils.extract[List[String]](config, "parameters", "fields", "mktCampaign")

        enrich =  CampaignsEnrichment(medium, source, term, content, campaign)
      } yield enrich).toValidationNel
    })
  }

}

// TODO docstring
case class MarketingCampaign(
  medium:   Option[String],
  source:   Option[String],
  term:     Option[String],
  content:  Option[String],
  campaign: Option[String]  
  )

/**
 * Config for a campaigns enrichment
 *
 * TODO docstring
 * @param domains List of internal domains
 */
case class CampaignsEnrichment(
  mktMedium:   List[String],
  mktSource:   List[String],
  mktTerm:     List[String],
  mktContent:  List[String],
  mktCampaign: List[String]
  ) extends Enrichment {

  val version = new DefaultArtifactVersion("0.1.0")

  /**
   * Extract the marketing fields from a URL.
   *
   * @param uri The URI to extract
   *        marketing fields from
   * @param encoding The encoding of
   *        the URI being parsed
   * @return the MarketingCampaign
   *         or an error message,
   *         boxed in a Scalaz
   *         Validation
   */
  def orderedExtraction(uri: URI, encoding: String): ValidationNel[String, MarketingCampaign] = {

    val parameters = try {
      URLEncodedUtils.parse(uri, encoding)
    } catch {
      case _ => return "Could not parse uri [%s]".format(uri).failNel[MarketingCampaign]
    }

    // Querystring map
    val sourceMap: SourceMap = parameters.map(p => (p.getName -> p.getValue)).toList.toMap

    val decodeString: TransformFunc = CU.decodeString(encoding, _, _)

    val medium = mktMedium.find(sourceMap.contains(_)).map(sourceMap(_))
    val source = mktSource.find(sourceMap.contains(_)).map(sourceMap(_))
    val term = mktTerm.find(sourceMap.contains(_)).map(sourceMap(_))
    val content = mktContent.find(sourceMap.contains(_)).map(sourceMap(_))
    val campaign = mktCampaign.find(sourceMap.contains(_)).map(sourceMap(_))

    MarketingCampaign(medium, source, term, content, campaign).success.toValidationNel
 }   

}
