package com.tencent.angel.ml.core

import com.tencent.angel.client.AngelClient
import com.tencent.angel.conf.AngelConf
import com.tencent.angel.ml.core.conf.SharedConf
import com.tencent.angel.ml.core.network.EvnContext
import com.tencent.angel.ml.core.variable.{PSVariable, VarState, VariableManager}
import com.tencent.angel.ml.math2.vector.Vector
import com.tencent.angel.model.{MatrixSaveContext, ModelSaveContext}

import scala.collection.JavaConversions._

class PSVariableManager private(isSparseFormat: Boolean) extends VariableManager {

  override def createALL(envCtx: EvnContext): Unit = {
    envCtx match {
      case AngelEvnContext(client: AngelClient) if client != null =>
        getALLVariables.foreach {
          case variable: PSVariable => client.addMatrix(variable.getMatrixCtx)
          case _ =>
        }
        client.createMatrices()
        getALLVariables.foreach { variable => variable.setState(VarState.Created) }
      case _ =>
        getALLVariables.foreach { variable => variable.create(envCtx) }
    }
  }

  override def loadALL(envCtx: EvnContext, path: String): Unit = {
    envCtx match {
      case AngelEvnContext(client: AngelClient) if client != null =>
        client.load()
        getALLVariables.foreach { variable => variable.setState(VarState.Initialized) }
      case _ =>
        getALLVariables.foreach { variable => variable.load(envCtx, path) }
    }
  }

  override def pullALL(epoch: Int, indices: Vector = null): Unit = {
    // val isSparseFormat = graph.dataFormat == "libsvm" || graph.dataFormat == "dummy"

    variables.values().foreach {
      case variable if isSparseFormat && variable.allowPullWithIndex =>
        variable.pull(epoch, indices)
      case variable => variable.pull(epoch)
    }
  }

  override def pull(name: String, epoch: Int = 0, indices: Vector = null): Unit = {
    // val isSparseFormat = graph.dataFormat == "libsvm" || graph.dataFormat == "dummy"

    val variable = getVariable(name)
    if (variable != null) {
      variable match {
        case v if isSparseFormat && v.allowPullWithIndex =>
          v.pull(epoch, indices)
        case v => v.pull(epoch)
      }
    }

  }

  override def saveALL(envCtx: EvnContext, path: String): Unit = {
    envCtx match {
      case AngelEvnContext(client: AngelClient) if client != null =>
        val sharedConf = SharedConf.get()
        val saveContext = new ModelSaveContext
        getALLVariables.foreach { variable =>
          assert(variable.getState == VarState.Initialized || variable.getState == VarState.Ready)
          saveContext.addMatrix(new MatrixSaveContext(variable.name, variable.formatClassName))
        }
        saveContext.setSavePath(sharedConf.get(AngelConf.ANGEL_JOB_OUTPUT_PATH))
        val deleteExistsFile = sharedConf.getBoolean(AngelConf.ANGEL_JOB_OUTPUT_PATH_DELETEONEXIST,
          AngelConf.DEFAULT_ANGEL_JOB_OUTPUT_PATH_DELETEONEXIST)
        client.save(saveContext, deleteExistsFile)
      case _ =>
        getALLVariables.foreach { variable => variable.save(envCtx, path) }
    }
  }
}

object PSVariableManager {
  private var vm: VariableManager = _

  def get(isSparseFormat: Boolean): VariableManager = synchronized {
    if (vm == null) {
      vm = new PSVariableManager(isSparseFormat)
    }
    vm
  }
}