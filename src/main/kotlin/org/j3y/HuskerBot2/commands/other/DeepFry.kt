package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.FileUpload
import org.j3y.HuskerBot2.commands.SlashCommand
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.color.ColorSpace
import java.awt.image.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.*
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import org.slf4j.LoggerFactory

// OpenCV (Bytedeco)
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.RectVector
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_imgproc.equalizeHist
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Component
class DeepFry() : SlashCommand() {
    private final val log = LoggerFactory.getLogger(DeepFry::class.java)

    companion object {
        private val overlayImages: List<BufferedImage> by lazy {
            try {
                val resolver = PathMatchingResourcePatternResolver(DeepFry::class.java.classLoader)
                val resources: Array<Resource> = resolver.getResources("classpath*:/deepfry/chars/*.png") + resolver.getResources("classpath*:/deepfry/emotes/*.png")
                resources.mapNotNull { res ->
                    try {
                        res.inputStream.use { ImageIO.read(it) }
                    } catch (_: Exception) { null }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        private val flareImages: List<BufferedImage> by lazy {
            try {
                val resolver = PathMatchingResourcePatternResolver(DeepFry::class.java.classLoader)
                val resources: Array<Resource> = resolver.getResources("classpath*:/deepfry/flares/*.png")
                resources.mapNotNull { res ->
                    try {
                        res.inputStream.use { ImageIO.read(it) }
                    } catch (_: Exception) { null }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
    private val restTemplate: RestTemplate = RestTemplate()
    override fun getCommandKey(): String = "deepfry"
    override fun getDescription(): String = "Download an image from a URL and randomly 'deep fry' it with meme-style filters."
    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "url", "Direct URL to an image (http/https)", true)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue() // public by default

        val url = commandEvent.getOption("url")?.asString?.trim()
        if (url.isNullOrBlank() || !isValidHttpUrl(url)) {
            commandEvent.hook.sendMessage("Please provide a valid http/https image URL.").queue()
            return
        }

        try {
            val img = downloadImage(url)
            if (img == null) {
                commandEvent.hook.sendMessage("Could not download that image. Make sure the URL is reachable and points to an image.").queue()
                return
            }

            // Limit extremely large images to a reasonable working size to avoid OOM
            val resized = resizeIfTooLarge(img, 2048, 2048)

            // Detect eyes and place random flares over them before adding any other overlays
            val withEyes = coverEyesWithFlares(resized)

            val withOverlays = addRandomOverlays(withEyes)
            val fried = deepFryRandom(withOverlays)

            val jpegBytes = toLowQualityJpegBytes(fried, quality = Random.nextDouble(0.05, 0.25).toFloat())
            val filename = "deepfried_${System.currentTimeMillis()}.jpg"

            commandEvent.hook.sendFiles(FileUpload.fromData(jpegBytes, filename)).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            commandEvent.hook.sendMessage("Image processing failed: ${e.message}").queue()
        }
    }

    private fun isValidHttpUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            scheme == "http" || scheme == "https"
        } catch (e: Exception) { false }
    }

    private fun downloadImage(url: String): BufferedImage? {
        // Use RestTemplate with headers and accept types
        val headers = HttpHeaders()
        headers.add(HttpHeaders.USER_AGENT, "HuskerBot2-DeepFry/1.0")
        headers.accept = listOf(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF, MediaType.ALL)

        val entity = HttpEntity<Void>(headers)
        val response = restTemplate.exchange(url, HttpMethod.GET, entity, ByteArray::class.java)
        if (!response.statusCode.is2xxSuccessful) return null

        val contentType = response.headers.contentType

        if (contentType == null || contentType.type.lowercase() != "image") {
            log.info("Content-Type: ${contentType?.type}")
            log.info("Response Code: ${response.statusCode}")
            return null
        }

        val data = response.body ?: return null
        val derp = ImageIO.read(ByteArrayInputStream(data))

        log.info("Derp {}", derp)

        return derp
    }

    private fun resizeIfTooLarge(img: BufferedImage, maxW: Int, maxH: Int): BufferedImage {
        val w = img.width
        val h = img.height
        if (w <= maxW && h <= maxH) return img
        val scale = min(maxW.toDouble() / w, maxH.toDouble() / h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.drawImage(img, 0, 0, nw, nh, null)
        g.dispose()
        return out
    }

    // Detect eyes using OpenCV and draw big red circles over them before overlays are added
    private fun coverEyesWithRedCircles(img: BufferedImage): BufferedImage {
        val eyes = detectEyes(img)
        if (eyes.isEmpty()) return img
        val out = toRgb(img)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(255, 0, 0)
            // Slight transparency to blend better but still be "big red circles"
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f)
            for (r in eyes) {
                val cx = r.x + r.width / 2.0
                val cy = r.y + r.height / 2.0
                val radius = (max(r.width, r.height) * 0.75).toInt().coerceAtLeast(4) // big enough to cover the eye
                val d = radius * 2
                g.fillOval((cx - radius).toInt(), (cy - radius).toInt(), d, d)
            }
        } finally {
            g.dispose()
        }
        return out
    }

    // Detect eyes and cover them with random flare overlays loaded from resources/deepfry/flares
    private fun coverEyesWithFlares(img: BufferedImage): BufferedImage {
        val eyes = detectEyes(img)
        if (eyes.isEmpty()) return img
        if (flareImages.isEmpty()) return img

        val rng = Random(System.nanoTime())
        val flare = flareImages[rng.nextInt(flareImages.size)]

        val out = toRgb(img)
        val g = out.createGraphics() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            for (r in eyes) {
                try {
                    if (flare.width <= 0 || flare.height <= 0) continue

                    // Eye center
                    val cx = r.x + r.width / 2.0
                    val cy = r.y + r.height / 2.0

                    // Target size: a bit larger than the eye
                    val baseSize = max(r.width, r.height).toDouble()
                    val target = (baseSize * rng.nextDouble(1.2, 2.2)).toInt().coerceAtLeast(8)

                    // Scale keeping aspect ratio so that the larger dimension matches target
                    val baseScale = target / max(flare.width, flare.height).toDouble()
                    val extra = rng.nextDouble(0.9, 1.3)
                    val scale = baseScale * extra
                    val ow = max(1, (flare.width * scale).toInt())
                    val oh = max(1, (flare.height * scale).toInt())

                    // Transparency and rotation
                    val alpha = rng.nextDouble(0.7, 1.0).toFloat()
                    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
                    val angleRad = Math.toRadians(rng.nextDouble(0.0, 360.0))

                    // Build transform so that the flare is centered at (cx, cy)
                    val tx = AffineTransform()
                    tx.translate(cx, cy)
                    tx.rotate(angleRad)
                    tx.scale(ow / flare.width.toDouble(), oh / flare.height.toDouble())
                    tx.translate(-flare.width / 2.0, -flare.height / 2.0)

                    g.drawImage(flare, tx, null)
                } catch (_: Exception) {
                    // skip this eye if something goes wrong
                }
            }
        } finally {
            g.dispose()
        }
        return out
    }

    private fun detectEyes(img: BufferedImage): List<Rectangle> {
        val cascade = getEyeCascade() ?: return emptyList()
        val w = img.width
        val h = img.height
        if (w < 8 || h < 8) return emptyList()

        // Convert to grayscale BufferedImage first for easy byte access
        val grayBI = BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY)
        val g = grayBI.createGraphics()
        try {
            g.drawImage(img, 0, 0, null)
        } finally {
            g.dispose()
        }

        val data = (grayBI.raster.dataBuffer as DataBufferByte).data
        val mat = Mat(h, w, CV_8UC1)
        // Fill Mat with grayscale bytes (BytePointer indexed copy)
        val ptr = mat.data()
        for (i in 0 until data.size) {
            ptr.put(i.toLong(), data[i])
        }

        // Improve detection a bit
        equalizeHist(mat, mat)

        val eyes = RectVector()
        try {
            // Reasonable defaults for eye detection on various sizes
            cascade.detectMultiScale(
                mat,
                eyes,
                1.1, // scaleFactor
                3,   // minNeighbors
                0,
                Size(max(8, w / 50), max(8, h / 50)),
                Size()
            )

            val result = ArrayList<Rectangle>(eyes.size().toInt())
            for (i in 0 until eyes.size().toInt()) {
                val r = eyes.get(i.toLong())
                result.add(Rectangle(r.x(), r.y(), r.width(), r.height()))
            }
            return result
        } catch (e: Exception) {
            log.warn("Eye detection failed: ${e.message}")
            return emptyList()
        } finally {
            eyes.close()
            mat.close()
        }
    }

    // Cache the cascade once per classloader
    private fun getEyeCascade(): CascadeClassifier? {
        return try {
            eyeCascadeSingleton ?: synchronized(DeepFry::class.java) {
                eyeCascadeSingleton ?: loadCascadeFromResources("deepfry/haarcascade_eye.xml").also { eyeCascadeSingleton = it }
            }
        } catch (e: Exception) {
            log.warn("Could not load eye cascade: ${e.message}")
            null
        }
    }

    private fun loadCascadeFromResources(resourceName: String): CascadeClassifier? {
        val cl = DeepFry::class.java.classLoader
        val resStream = cl.getResourceAsStream(resourceName) ?: cl.getResourceAsStream("/$resourceName")
        if (resStream == null) {
            log.warn("Cascade resource not found: $resourceName")
            return null
        }
        return resStream.use { input ->
            val tmp = File.createTempFile("cascade_", ".xml")
            tmp.deleteOnExit()
            Files.copy(input, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            val cc = CascadeClassifier(tmp.absolutePath)
            if (cc.empty()) {
                log.warn("Cascade classifier is empty for $resourceName")
                null
            } else cc
        }
    }

    private var eyeCascadeSingleton: CascadeClassifier? = null

    private fun addRandomOverlays(img: BufferedImage): BufferedImage {
        val base = toRgb(img)
        val w = base.width
        val h = base.height

        if (overlayImages.isEmpty()) return base

        val rng = Random(System.nanoTime())
        val maxCount = overlayImages.size.coerceAtMost(10)
        val minCount = min(5, maxCount)
        if (minCount == 0) return base
        val count = rng.nextInt(minCount, maxCount + 1)

        val selected = overlayImages.shuffled(rng).take(count)

        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        // draw base first
        g.drawImage(base, 0, 0, null)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        for (overlay in selected) {
            try {
                val baseMin = min(w, h).toDouble()
                val target = (rng.nextDouble(0.10, 0.35) * baseMin).toInt().coerceAtLeast(8)
                // Base scale to fit the overlay relative to the target size
                val baseScale = target / max(overlay.width, overlay.height).toDouble()
                // Additional random resize between -50% and 0% (i.e., multiply by 0.5..1.0)
                val resizeFactor = rng.nextDouble(0.5, 1.0)
                val scale = baseScale * resizeFactor
                val ow = max(1, (overlay.width * scale).toInt())
                val oh = max(1, (overlay.height * scale).toInt())

                val maxX = (w - ow).coerceAtLeast(0)
                val maxY = (h - oh).coerceAtLeast(0)
                val x = if (maxX == 0) 0 else rng.nextInt(0, maxX + 1)
                val y = if (maxY == 0) 0 else rng.nextInt(0, maxY + 1)

                // Random global alpha overlay in addition to PNG alpha
                val alpha = rng.nextDouble(0.65, 1.0).toFloat()
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)

                // Random rotation in degrees [0, 360)
                val angleRad = Math.toRadians(rng.nextDouble(0.0, 360.0))

                // Build transform: move to destination center, rotate, scale to desired size, then
                // shift back by the overlay image's center so rotation is around its center.
                val tx = AffineTransform()
                tx.translate(x + ow / 2.0, y + oh / 2.0)
                tx.rotate(angleRad)
                tx.scale(ow / overlay.width.toDouble(), oh / overlay.height.toDouble())
                tx.translate(-overlay.width / 2.0, -overlay.height / 2.0)

                (g as Graphics2D).drawImage(overlay, tx, null)
            } catch (_: Exception) {
                // skip problematic overlay
            }
        }

        g.dispose()
        return out
    }

    private fun deepFryRandom(img: BufferedImage): BufferedImage {
        var work = toRgb(img)
        val rng = Random(System.nanoTime())

        // Available filters
        val filters = mutableListOf<(BufferedImage) -> BufferedImage>(
            { oversaturate(it, rng.nextDouble(1.6, 2.5).toFloat()) },
            { adjustBrightnessContrast(it, rng.nextDouble(-0.05, 0.15).toFloat(), rng.nextDouble(1.2, 1.8).toFloat()) },
            { addNoise(it, amount = rng.nextDouble(0.04, 0.15).toFloat()) },
            { sharpen(it, intensity = rng.nextDouble(0.6, 1.4).toFloat()) },
            { vignette(it, strength = rng.nextDouble(0.3, 0.7).toFloat()) },
            { scanlines(it, opacity = rng.nextDouble(0.05, 0.15).toFloat(), spacing = rng.nextInt(2, 5)) },
            { chromaticAberration(it, shift = rng.nextInt(1, 4)) },
            { jpegRecompress(it, quality = rng.nextDouble(0.05, 0.25).toFloat()) },
            { posterize(it, levels = rng.nextInt(3, 6)) },
        )

        // Randomly pick 4-7 filters and shuffle their order
        filters.shuffle(rng)
        val count = rng.nextInt(4, min(8, filters.size + 1))
        for (i in 0 until count) {
            work = filters[i](work)
        }
        return work
    }

    // Ensure we have an RGB image buffer
    private fun toRgb(img: BufferedImage): BufferedImage {
        if (img.type == BufferedImage.TYPE_INT_RGB) return img
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.drawImage(img, 0, 0, null)
        g.dispose()
        return out
    }

    private fun toLowQualityJpegBytes(img: BufferedImage, quality: Float = 0.15f): ByteArray {
        val baos = ByteArrayOutputStream()
        val writers: Iterator<ImageWriter> = ImageIO.getImageWritersByFormatName("jpg")
        val writer = if (writers.hasNext()) writers.next() else throw IllegalStateException("No JPEG writer available")
        val ios = MemoryCacheImageOutputStream(baos)
        writer.output = ios
        val param: ImageWriteParam = writer.defaultWriteParam
        if (param.canWriteCompressed()) {
            param.compressionMode = ImageWriteParam.MODE_EXPLICIT
            param.compressionQuality = quality.coerceIn(0.01f, 1.0f)
        }
        writer.write(null, IIOImage(img, null, null), param)
        ios.flush()
        writer.dispose()
        return baos.toByteArray()
    }

    // Apply an extra JPEG recompress step to introduce artifacts
    private fun jpegRecompress(img: BufferedImage, quality: Float): BufferedImage {
        val bytes = toLowQualityJpegBytes(img, quality)
        return ImageIO.read(ByteArrayInputStream(bytes))
    }

    private fun oversaturate(img: BufferedImage, saturation: Float): BufferedImage {
        val cm = ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), null)
        val linear = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        cm.filter(img, linear)

        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until linear.height) {
            for (x in 0 until linear.width) {
                val rgb = linear.getRGB(x, y)
                var r = (rgb shr 16 and 0xFF) / 255f
                var g = (rgb shr 8 and 0xFF) / 255f
                var b = (rgb and 0xFF) / 255f

                val maxc = max(r, max(g, b))
                val minc = min(r, min(g, b))
                val l = (maxc + minc) / 2f
                val sOld = if (maxc == minc) 0f else if (l < 0.5f) (maxc - minc) / (maxc + minc) else (maxc - minc) / (2.0f - maxc - minc)
                val sNew = (sOld * saturation)

                // Simple saturation scaling in RGB: push away from gray
                val gray = (r + g + b) / 3f
                r = gray + (r - gray) * (1 + (sNew - sOld))
                g = gray + (g - gray) * (1 + (sNew - sOld))
                b = gray + (b - gray) * (1 + (sNew - sOld))

                val rr = (r * 255f).toInt().coerceIn(0, 255)
                val gg = (g * 255f).toInt().coerceIn(0, 255)
                val bb = (b * 255f).toInt().coerceIn(0, 255)
                out.setRGB(x, y, (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb)
            }
        }
        return out
    }

    private fun adjustBrightnessContrast(img: BufferedImage, brightness: Float, contrast: Float): BufferedImage {
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val b = brightness
        val c = contrast
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16 and 0xFF)
                val g = (rgb shr 8 and 0xFF)
                val bl = (rgb and 0xFF)
                fun adj(v: Int): Int {
                    val f = v / 255f
                    val f2 = ((f - 0.5f) * c + 0.5f) + b
                    return (f2 * 255f).toInt().coerceIn(0, 255)
                }
                val rr = adj(r)
                val gg = adj(g)
                val bb = adj(bl)
                out.setRGB(x, y, (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb)
            }
        }
        return out
    }

    private fun addNoise(img: BufferedImage, amount: Float): BufferedImage {
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val rng = Random(System.nanoTime())
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16 and 0xFF)
                val g = (rgb shr 8 and 0xFF)
                val b = (rgb and 0xFF)
                val n = ((rng.nextFloat() - 0.5f) * 255f * amount).toInt()
                val rr = (r + n).coerceIn(0, 255)
                val gg = (g + n).coerceIn(0, 255)
                val bb = (b + n).coerceIn(0, 255)
                out.setRGB(x, y, (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb)
            }
        }
        return out
    }

    private fun kernelConvolve(img: BufferedImage, kernel: Kernel): BufferedImage {
        try {
            val op = ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null)
            val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
            op.filter(img, out)
            return out
        } catch (e: Exception) {
            return img
        }
    }

    private fun sharpen(img: BufferedImage, intensity: Float): BufferedImage {
        val i = intensity.coerceIn(0.1f, 2.0f)
        val k = floatArrayOf(
            0f, -i, 0f,
            -i, 1f + 4f * i, -i,
            0f, -i, 0f
        )
        val kernel = Kernel(3, 3, k)
        return kernelConvolve(img, kernel)
    }

    private fun vignette(img: BufferedImage, strength: Float): BufferedImage {
        val w = img.width
        val h = img.height
        val cx = w / 2.0
        val cy = h / 2.0
        val maxDist = Math.hypot(cx, cy)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                val d = Math.hypot(x - cx, y - cy)
                val v = (1.0 - (d / maxDist) * strength).coerceIn(0.0, 1.0)
                val rr = (r * v).toInt().coerceIn(0, 255)
                val gg = (g * v).toInt().coerceIn(0, 255)
                val bb = (b * v).toInt().coerceIn(0, 255)
                out.setRGB(x, y, (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb)
            }
        }
        return out
    }

    private fun scanlines(img: BufferedImage, opacity: Float, spacing: Int): BufferedImage {
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.drawImage(img, 0, 0, null)
        g.color = Color(0, 0, 0, (opacity.coerceIn(0f, 1f) * 255).toInt())
        for (y in 0 until img.height step spacing) {
            g.drawLine(0, y, img.width, y)
        }
        g.dispose()
        return out
    }

    private fun chromaticAberration(img: BufferedImage, shift: Int): BufferedImage {
        val w = img.width
        val h = img.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                fun sample(ix: Int, iy: Int): Int {
                    val sx = ix.coerceIn(0, w - 1)
                    val sy = iy.coerceIn(0, h - 1)
                    return img.getRGB(sx, sy)
                }
                val rPix = sample(x + shift, y)
                val gPix = sample(x, y)
                val bPix = sample(x - shift, y)
                val r = (rPix shr 16) and 0xFF
                val g = (gPix shr 8) and 0xFF
                val b = bPix and 0xFF
                out.setRGB(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return out
    }

    private fun posterize(img: BufferedImage, levels: Int): BufferedImage {
        val lv = levels.coerceIn(2, 16)
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val step = 256 / lv
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                fun q(v: Int): Int {
                    val qv = (v / step) * step
                    return qv.coerceIn(0, 255)
                }
                val rr = q(r)
                val gg = q(g)
                val bb = q(b)
                out.setRGB(x, y, (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb)
            }
        }
        return out
    }
}
