/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.sql.execution

import org.apache.gluten.backendsapi.BackendsApiManager
import org.apache.gluten.sql.shims.SparkShimLoader

import org.apache.spark.internal.Logging
import org.apache.spark.serializer.Serializer
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.plans.physical._
import org.apache.spark.sql.execution.exchange._

// The write is Velox RowVector, but the reader transforms it to cudf table
case class GPUColumnarShuffleExchangeExec(
    override val outputPartitioning: Partitioning,
    child: SparkPlan,
    shuffleOrigin: ShuffleOrigin = ENSURE_REQUIREMENTS,
    projectOutputAttributes: Seq[Attribute],
    advisoryPartitionSize: Option[Long] = None)
  extends ColumnarShuffleExchangeExecBase(outputPartitioning, child, projectOutputAttributes) {

  // super.stringArgs ++ Iterator(output.map(o => s"${o}#${o.dataType.simpleString}"))
  val serializer: Serializer = BackendsApiManager.getSparkPlanExecApiInstance
    .createColumnarBatchSerializer(schema, metrics, shuffleWriterType, true)

  override def nodeName: String = "CudfColumnarExchange"

  protected def withNewChildInternal(newChild: SparkPlan): GPUColumnarShuffleExchangeExec =
    copy(child = newChild)

  override def getSerializer: Serializer = serializer
}

object GPUColumnarShuffleExchangeExec extends Logging {

  def apply(
      plan: ShuffleExchangeExec,
      child: SparkPlan,
      shuffleOutputAttributes: Seq[Attribute]): GPUColumnarShuffleExchangeExec = {
    GPUColumnarShuffleExchangeExec(
      plan.outputPartitioning,
      child,
      plan.shuffleOrigin,
      shuffleOutputAttributes,
      advisoryPartitionSize = SparkShimLoader.getSparkShims.getShuffleAdvisoryPartitionSize(plan)
    )
  }
}
