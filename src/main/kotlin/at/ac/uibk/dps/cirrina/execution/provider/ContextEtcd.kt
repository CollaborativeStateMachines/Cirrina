package at.ac.uibk.dps.cirrina.execution.provider

import at.ac.uibk.dps.cirrina.execution.`object`.Context
import at.ac.uibk.dps.cirrina.execution.`object`.ContextVariable
import at.ac.uibk.dps.cirrina.execution.util.Serializer
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.Client
import io.etcd.jetcd.op.Cmp
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.op.Op
import io.etcd.jetcd.options.DeleteOption
import io.etcd.jetcd.options.GetOption
import io.etcd.jetcd.options.PutOption
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class ContextEtcd(endpoints: List<String>) : Context {
  private val client: Client = Client.builder().endpoints(*endpoints.toTypedArray()).build()

  private val rootKey = ByteSequence.from(byteArrayOf(0))
  private val matchAll = GetOption.builder().withRange(rootKey).build()

  override fun has(name: String): Boolean {
    val resp =
      client.kvClient
        .get(name.toByteSequence(), GetOption.builder().withCountOnly(true).build())
        .sync()
    return resp.count > 0L
  }

  override fun get(name: String): Any? {
    val resp = client.kvClient.get(name.toByteSequence()).sync()
    val kv = resp.kvs.firstOrNull() ?: error("variable '$name' does not exist")
    return kv.value.bytes.fromBytes()
  }

  override fun create(name: String, value: Any?): Int {
    val key = name.toByteSequence()
    val bytes = value?.toBytes() ?: byteArrayOf()

    val txn =
      client.kvClient
        .txn()
        .If(Cmp(key, Cmp.Op.EQUAL, CmpTarget.createRevision(0)))
        .Then(Op.put(key, ByteSequence.from(bytes), PutOption.DEFAULT))
        .commit()
        .sync()

    if (!txn.isSucceeded) error("variable '$name' already exists")
    return bytes.size
  }

  override fun assign(name: String, value: Any?): Int {
    val key = name.toByteSequence()
    val bytes = value?.toBytes() ?: byteArrayOf()

    val txn =
      client.kvClient
        .txn()
        .If(Cmp(key, Cmp.Op.GREATER, CmpTarget.createRevision(0)))
        .Then(Op.put(key, ByteSequence.from(bytes), PutOption.DEFAULT))
        .commit()
        .sync()

    if (!txn.isSucceeded) error("variable '$name' does not exist")
    return bytes.size
  }

  override fun delete(name: String) {
    val resp = client.kvClient.delete(name.toByteSequence()).sync()
    if (resp.deleted == 0L) error("variable '$name' does not exist")
  }

  override fun deleteAll() {
    val options = DeleteOption.builder().withRange(rootKey).build()
    client.kvClient.delete(rootKey, options).sync()
  }

  override fun getAll(): List<ContextVariable> {
    val resp = client.kvClient.get(rootKey, matchAll).sync()
    return resp.kvs.map { kv ->
      ContextVariable.eager(kv.key.toString(StandardCharsets.UTF_8), kv.value.bytes.fromBytes())
    }
  }

  override fun clear() {}

  private fun <T> CompletableFuture<T>.sync(): T = this.get()

  private fun String.toByteSequence() = ByteSequence.from(this, StandardCharsets.UTF_8)

  private fun Any.toBytes(): ByteArray = Serializer.serialize(this)

  private fun ByteArray.fromBytes(): Any = Serializer.deserialize(this)

  override fun close() {
    client.close()
  }
}
