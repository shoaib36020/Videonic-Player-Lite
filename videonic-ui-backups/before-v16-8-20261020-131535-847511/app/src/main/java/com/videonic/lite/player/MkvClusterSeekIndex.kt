package com.videonic.lite.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * A bounded, read-only index of Matroska Cluster offsets. Unlike the Matroska Cues
 * table this can be reconstructed without changing/copying the video file.
 * Only used after both the regular and platform parsers declare a local MKV unseekable.
 * A Cluster is an approximate seek point, not necessarily an exact keyframe.
 */
@OptIn(UnstableApi::class)
internal class MkvClusterSeekIndex private constructor(
    private val timestampsUs: LongArray,
    private val offsets: LongArray,
    val fileSize: Long
) {
    fun asSeekMap(durationUs: Long): SeekMap = object : SeekMap {
        override fun isSeekable(): Boolean = timestampsUs.size > 1
        override fun getDurationUs(): Long = durationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            var low=0
            var high=timestampsUs.lastIndex
            val target=timeUs.coerceAtLeast(0L)
            while(low<=high) {
                val mid=(low+high) ushr 1
                if(timestampsUs[mid]<=target) low=mid+1 else high=mid-1
            }
            val left=high.coerceIn(0,timestampsUs.lastIndex)
            val first=SeekPoint(timestampsUs[left],offsets[left])
            val right=(left+1).coerceAtMost(timestampsUs.lastIndex)
            return if(right==left) SeekMap.SeekPoints(first)
              else SeekMap.SeekPoints(first,SeekPoint(timestampsUs[right],offsets[right]))
        }
    }

    companion object {
        // EBML: ID bytes are variable-length (marker bits are part of the ID);
        // sizes are EBML variable integers (marker bits removed).
        private data class Element(val id:Long,val dataAt:Long,val length:Long,val nextAt:Long)
        private fun readByte(channel:FileChannel,at:Long,limit:Long):Int {
            if(at<0L || at>=limit) return -1
            val b=ByteBuffer.allocate(1)
            channel.position(at)
            return if(channel.read(b)==1) b.array()[0].toInt() and 0xff else -1
        }
        private fun element(channel:FileChannel,at:Long,limit:Long):Element? {
            val first=readByte(channel,at,limit)
            if(first<=0) return null
            var mask=0x80;var idBytes=1
            while((first and mask)==0 && idBytes<5) {mask=mask ushr 1;idBytes++}
            if(idBytes>4) return null
            var id=0L
            for(i in 0 until idBytes) {
                val b=readByte(channel,at+i,limit)
                if(b<0)return null
                id=(id shl 8) or b.toLong()
            }
            val sizeAt=at+idBytes
            val firstSize=readByte(channel,sizeAt,limit)
            if(firstSize<0)return null
            var width=1;var sizeMask=0x80
            while((firstSize and sizeMask)==0 && width<=8) {sizeMask=sizeMask ushr 1;width++}
            if(width>8)return null
            var length=(firstSize and (sizeMask-1)).toLong()
            for(i in 1 until width) {
                val b=readByte(channel,sizeAt+i,limit)
                if(b<0)return null
                length=(length shl 8) or b.toLong()
            }
            val isUnknown=length==((1L shl (width*7))-1L)
            val dataAt=sizeAt+width
            if(dataAt>limit)return null
            val lengthSafe=if(isUnknown) limit-dataAt else length
            if(lengthSafe<0L || lengthSafe>limit-dataAt)return null
            return Element(id,dataAt,lengthSafe,dataAt+lengthSafe)
        }
        private fun number(channel:FileChannel,start:Long,length:Long,limit:Long):Long? {
            if(length !in 1L..8L || start+length>limit)return null
            var value=0L
            for(i in 0L until length) {
                val b=readByte(channel,start+i,limit)
                if(b<0)return null
                value=(value shl 8) or b.toLong()
            }
            return value
        }
        fun scan(context:Context,uri:Uri):MkvClusterSeekIndex? = runCatching {
            context.contentResolver.openFileDescriptor(uri,"r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use {channel ->
                    val length=channel.size()
                    if(length<1024)return@use null
                    val startNs=System.nanoTime()
                    val deadline=startNs+20_000_000_000L
                    var offset=0L
                    // Read EBML header and the Segment's offset, without reading media blocks.
                    var segment:Element?=null
                    var roots=0
                    while(segment==null && offset<length && roots++<16) {
                        val node=element(channel,offset,length) ?:break
                        if(node.id==0x18538067L) segment=node
                        else offset=node.nextAt
                    }
                    val root=segment ?:return@use null
                    var pos=root.dataAt
                    val end=root.nextAt
                    var scaleNs=1_000_000L
                    val timecodes=ArrayList<Long>(2048)
                    val offsets=ArrayList<Long>(2048)
                    var count=0
                    while(pos<end && count<200_000 && System.nanoTime()<deadline) {
                        val node=element(channel,pos,end) ?:break
                        when(node.id) {
                            0x1549A966L -> { // Info / TimestampScale
                                var child=node.dataAt
                                while(child<node.nextAt) {
                                    val sub=element(channel,child,node.nextAt) ?:break
                                    if(sub.id==0x2AD7B1L) {
                                        scaleNs=number(channel,sub.dataAt,sub.length,node.nextAt) ?:scaleNs
                                    }
                                    child=sub.nextAt
                                }
                            }
                            0x1F43B675L -> { // Cluster; its Timestamp is usually first
                                var child=node.dataAt
                                val headerEnd=(node.dataAt+65536L).coerceAtMost(node.nextAt)
                                var tick:Long?=null
                                while(child<headerEnd) {
                                    val sub=element(channel,child,node.nextAt) ?:break
                                    if(sub.id==0xE7L) { // Timestamp
                                        tick=number(channel,sub.dataAt,sub.length,node.nextAt)
                                        break
                                    }
                                    child=sub.nextAt
                                }
                                if(tick!=null) {
                                    timecodes.add(tick)
                                    offsets.add(pos) // absolute Cluster ID position
                                }
                                count++
                            }
                        }
                        if(node.nextAt<=pos)break
                        pos=node.nextAt
                    }
                    // Do not publish an incomplete index: late seeking would otherwise
                    // jump to the last scanned Cluster instead of the requested time.
                    if(pos<end || timecodes.size<2 || scaleNs !in 1L..1_000_000_000L) return@use null
                    val paired=timecodes.indices.map {i ->
                        ((timecodes[i]*scaleNs)/1000L) to offsets[i]
                    }.filter { it.first>=0L && it.second>=root.dataAt }
                        .sortedBy { it.first }
                        .distinctBy { it.first }
                    if(paired.size<2)return@use null
                    MkvClusterSeekIndex(paired.map{it.first}.toLongArray(),
                        paired.map{it.second}.toLongArray(),length)
                }
            }
        }.getOrNull()
    }
}

/** Installs a reconstructed cluster SeekMap ONLY if Media3 reports no seek map. */
@OptIn(UnstableApi::class)
internal class MkvIndexedExtractorsFactory(
    private val context:Context,
    private val uri:Uri,
    private val preparedIndex:MkvClusterSeekIndex?=null
):ExtractorsFactory {
    private val delegate=DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
    // Scanning is normally done on a worker thread before switching the live player.
    // Only rescan if a caller directly constructs the factory without a prepared index.
    private val index by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        preparedIndex ?: MkvClusterSeekIndex.scan(context,uri)
    }
    override fun createExtractors():Array<Extractor> {
        val idx=index ?:return delegate.createExtractors()
        return delegate.createExtractors().map { extractor ->
            if(extractor is MatroskaExtractor) object:Extractor {
                private var eligible=true
                override fun sniff(input:ExtractorInput):Boolean {
                    eligible=input.length==idx.fileSize
                    return extractor.sniff(input)
                }
                override fun init(output:ExtractorOutput) {
                    extractor.init(object:ExtractorOutput {
                        override fun track(id:Int,type:Int):TrackOutput=output.track(id,type)
                        override fun endTracks()=output.endTracks()
                        override fun seekMap(map:SeekMap) {
                            output.seekMap(if(eligible && !map.isSeekable)idx.asSeekMap(map.durationUs) else map)
                        }
                    })
                }
                override fun read(input:ExtractorInput,seekPosition:PositionHolder):Int=
                    extractor.read(input,seekPosition)
                override fun seek(position:Long,timeUs:Long)=extractor.seek(position,timeUs)
                override fun release()=extractor.release()
            } else extractor
        }.toTypedArray()
    }
}
