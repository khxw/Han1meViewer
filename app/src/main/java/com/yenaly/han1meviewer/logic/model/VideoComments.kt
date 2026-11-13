package com.yenaly.han1meviewer.logic.model

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/20 020 21:56
 */
data class VideoComments(
    val videoComment: MutableList<VideoComment>,
    val currentUserId: String? = null,
    val csrfToken: String? = null,
) {
    data class VideoComment(
        // 頭像
        val avatar: String,
        // 作者
        val username: String,
        // 發佈日期
        val date: String,
        // 内容
        val content: String,
        // 點讚數量，登入前不能憑藉post獲取
        var thumbUp: Int? = null,
        // 點踩數量，登入前不能憑藉post獲取
        var thumbDown: Int? = null,
        // 是否为子评论
        val isChildComment: Boolean,
        // 是否有更多回覆
        val hasMoreReplies: Boolean = false,
        //更多回复数量
        val replyCount: Int? = 0,
        // 評論id，登入前不能憑藉post獲取
        val id: String? = "-1",
        // post 相關
        val post: POST,
        //举报相关
        val redirectUrl: String? = null,
        val reportableId: String? = null,
        val reportableType: String? = null
    ) {

        val replyTargetIdOrNull get() = post.foreignId ?: id
        val stableKey get() = replyTargetIdOrNull
            ?: reportableId
            ?: buildString {
                append(username)
                append('|')
                append(date)
                append('|')
                append(content.hashCode())
            }
        val realLikesCount get() = thumbUp
        val realDislikesCount get() = thumbDown

        /**
         * 点赞 / 取消点赞。
         *
         * 站点是「赞 / 踩」二选一：点赞时如果之前点过踩，要把踩数也减 1。
         */
        fun incLikesCount(cancel: Boolean = false): VideoComment {
            if (thumbUp == null && thumbDown == null) return this
            val wasDisliked = post.unlikeCommentStatus
            return copy(
                thumbUp = ((thumbUp ?: 0) + if (cancel) -1 else 1).coerceAtLeast(0),
                thumbDown = if (!cancel && wasDisliked) {
                    (thumbDown ?: 0).minus(1).coerceAtLeast(0)
                } else {
                    thumbDown
                },
                post = post.copy(
                    likeCommentStatus = !cancel,
                    unlikeCommentStatus = false
                )
            )
        }

        /**
         * 点踩 / 取消点踩。
         *
         * 点踩时如果之前点过赞，要把赞数也减 1。
         */
        fun decLikesCount(cancel: Boolean = false): VideoComment {
            if (thumbUp == null && thumbDown == null) return this
            val wasLiked = post.likeCommentStatus
            return copy(
                thumbDown = ((thumbDown ?: 0) + if (cancel) -1 else 1).coerceAtLeast(0),
                thumbUp = if (!cancel && wasLiked) {
                    (thumbUp ?: 0).minus(1).coerceAtLeast(0)
                } else {
                    thumbUp
                },
                post = post.copy(
                    likeCommentStatus = false,
                    unlikeCommentStatus = !cancel
                )
            )
        }

        data class POST(
            // 對方id
            val foreignId: String? = null,
            // 你點的
            var isPositive: Boolean = false,
            // 你的id
            val likeUserId: String? = null,
            var commentLikesCount: Int? = null,
            var commentLikesSum: Int? = null,
            // 你之前有沒有點過讚
            var likeCommentStatus: Boolean = false,
            // 你之前有沒有點過踩
            var unlikeCommentStatus: Boolean = false,
        )
    }
}

/**
 * 用於 評論交互 Flow 的返回值
 */
data class VideoCommentArgs(
    // 當前評論所處adapter位置
    val commentPosition: Int,
    // 你當前點擊的是讚還是踩，和comment裏的isPositive不一樣
    val isPositive: Boolean,
    val comment: VideoComments.VideoComment,
)
