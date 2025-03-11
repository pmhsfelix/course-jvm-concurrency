package org.pedrofelix.concurrency.course.utils

/**
 * A double-linked list with O(1) removal, achieved by having the insertion operation return a node,
 * which then can be provided to the remove operation.
 */
class NodeLinkedList<T> {
    interface Node<T> {
        val value: T
        val isInserted: Boolean
    }

    private class NodeImpl<T>(
        val maybeValue: T?,
    ) : Node<T> {
        constructor (
            maybeValue: T?,
            n: NodeImpl<T>,
            p: NodeImpl<T>,
        ) : this(maybeValue) {
            next = n
            prev = p
        }

        var next: NodeImpl<T>? = null
        var prev: NodeImpl<T>? = null

        override val value: T
            get() {
                require(maybeValue != null) { "Only nodes with non-null values can be exposed publicly" }
                return maybeValue
            }

        override val isInserted: Boolean
            get() = next != null && prev != null
    }

    private val head: NodeImpl<T> = NodeImpl(null)

    init {
        head.next = head
        head.prev = head
    }

    var count = 0
        private set

    fun addLast(value: T): Node<T> {
        val tail: NodeImpl<T> = head.prev
            ?: throw IllegalStateException("The list is in an invalid state: head.prev cannot be null")
        val node: NodeImpl<T> = NodeImpl(value, head, tail)
        head.prev = node
        tail.next = node
        count += 1
        return node
    }

    val empty: Boolean
        get() = head === head.prev

    val notEmpty: Boolean
        get() = !empty

    val headValue: T?
        get() {
            return if (notEmpty) {
                val firstNode = head.next
                    ?: throw IllegalStateException("The list is in an invalid state: head.next cannot be null")
                firstNode.value
            } else {
                null
            }
        }

    val headNode: Node<T>?
        get() {
            return if (notEmpty) {
                head.next
            } else {
                null
            }
        }

    fun isHeadNode(node: Node<T>) = head.next === node

    inline fun headCondition(cond: (T) -> Boolean): Boolean = headValue?.let { cond(it) } == true

    fun getAndRemoveFirst(): Node<T> {
        require(!empty) { "cannot pull from an empty list" }
        val node = head.next!!
        val nodeNext = node.next
        val nodePrev = node.prev
        require(nodeNext != null)
        require(nodePrev != null)
        head.next = nodeNext
        nodeNext.prev = head
        count -= 1
        node.next = null
        node.prev = null
        return node
    }

    fun remove(node: Node<T>) {
        require(node is NodeImpl<T>) { "node must be an internal node" }
        val nodeNext = node.next
        val nodePrev = node.prev
        require(nodeNext != null)
        require(nodePrev != null)
        nodePrev.next = node.next
        nodeNext.prev = node.prev
        node.next = null
        node.prev = null
        count -= 1
    }
}
