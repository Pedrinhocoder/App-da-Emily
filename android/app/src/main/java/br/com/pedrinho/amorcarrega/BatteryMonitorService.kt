package br.com.pedrinho.amorcarrega

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo
import kotlin.math.max

class BatteryMonitorService : Service() {
    companion object {
        const val CHANNEL = "battery_alerts"
        const val SERVICE_CHANNEL = "monitor"
        const val ACTION_STOP_ALERT = "br.com.pedrinho.amorcarrega.STOP_ALERT"
        // Disparada pelo botão "Desligar alerta" da notificação (funciona com a tela bloqueada).
        const val ACTION_DISMISS_ALERT = "br.com.pedrinho.amorcarrega.DISMISS_ALERT"
        const val ALERTS_ENABLED_KEY = "alerts_enabled"

        fun computeLevels(threshold: Int): Triple<Int, Int, Int> {
            // Primeiro alerta é configurável; os dois níveis fortes são fixos.
            val t1 = threshold.coerceIn(16, 50)
            val t2 = 15
            val t3 = 5
            return Triple(t1, t2, t3)
        }

        fun alertsEnabled(context: Context): Boolean =
            context.getSharedPreferences("config", Context.MODE_PRIVATE)
                .getBoolean(ALERTS_ENABLED_KEY, true)

        fun stopSound() {
            AlertEngine.stopImmediately()
        }
    }

    private var lastLevel = -1
    private var lastPlugged = false
    private var receiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()

        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Amor, Cuida ♥ · bateria")
            .setContentText("Monitorando a bateria.")
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(10, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(10, notification)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_STOP_ALERT) {
                    AlertEngine.cancelBatteryAlerts(context)
                    return
                }
                if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (scale > 0) (level * 100 / scale) else level
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

                if (!lastPlugged && plugged) {
                    AlertEngine.cancelBatteryAlerts(context)
                }
                lastPlugged = plugged

                if (plugged) {
                    lastLevel = pct
                    return
                }

                // Monitoramento continua funcionando mesmo com os alertas desligados.
                // Isso permite silenciar o app (igreja, reunião, cinema etc.) sem parar o serviço.
                if (!alertsEnabled(context)) {
                    AlertEngine.cancelBatteryAlerts(context)
                    lastLevel = pct
                    return
                }

                val threshold = getSharedPreferences("config", MODE_PRIVATE)
                    .getInt("threshold", 30)
                val (t1, t2, t3) = computeLevels(threshold)

                // Cada marco é tratado separadamente. Isso garante que 1%
                // dispare mesmo quando a bateria cai de 5% diretamente para 1%,
                // sem repetir o alerta enquanto o percentual permanece no mesmo nível.
                val crossed = when {
                    lastLevel < 0 -> pct <= t1
                    pct <= 1 && lastLevel > 1 -> true
                    pct <= t3 && lastLevel > t3 -> true
                    pct <= t2 && lastLevel > t2 -> true
                    pct <= t1 && lastLevel > t1 -> true
                    else -> false
                }

                if (crossed) AlertEngine.fire(context, pct, false)
                lastLevel = pct
            }
        }

        // Android 14+ exige que receivers registrados dinamicamente informem
        // explicitamente se são exportados. Este receiver só precisa receber
        // broadcasts do sistema e da própria aplicação.
        receiver?.let { registeredReceiver ->
            ContextCompat.registerReceiver(
                this,
                registeredReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(ACTION_STOP_ALERT)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL, "Monitoramento",
            NotificationManager.IMPORTANCE_LOW
        )

        val alertChannel = NotificationChannel(
            CHANNEL, "Alertas de bateria",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas importantes para lembrar de carregar o celular."
            enableVibration(true)
            setSound(null, null)
        }

        nm.createNotificationChannel(serviceChannel)
        nm.createNotificationChannel(alertChannel)
    }

    override fun onDestroy() {
        receiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        stopSound()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

object LoveMessages {
    val reminder = listOf(
        "Amor, já tá ficando sem bateria... coloca pra carregar que eu quero conversar com você até tarde ❤️",
        "Oi meu bem, o celular tá pedindo uma ajudinha aqui... bota o carregador, princesa 💕",
        "Meu amor, o nível tá baixando! Cuida do celular assim como você cuida do meu coração 🥰",
        "Oi vida, a bateria tá lá embaixo! Liga o carregador que a saudade já tá do meu lado 😘",
        "Meu bem, sem bateria = sem mim. Bota pra carregar logo que quero te mandar um audiozinho ❤️💋",
        "Amorzinho, ajuda o celular a sobreviver mais um pouco? Bota no carregador, princesa 🌸",
        "Vida, o celular tá morrendo... igual eu de saudade de você. Coloca pra carregar! 😭❤️",
        "Oi meu amor, a bateria está em estado de atenção! Cuida dele pra eu não ficar sem te encher o saco 😏💕"
    )

    val level20 = listOf(
        "Amor, chegamos nos 20%... isso aqui é um pedido carinhoso pra colocar o celular pra carregar ❤️🔋",
        "Meu bem, seu celular entrou nos 20%! Salva a bateria antes que ela decida ir embora 🥺💕",
        "20% de bateria e 100% de vontade de falar com você. Coloca pra carregar, meu amor 💗⚡",
        "Oi, vida! O celular avisou que está nos 20%. Acho que ele também quer um pouquinho de carinho 🔌🥰",
        "Amorzinho, 20% já é aquele momento de procurar o carregador. Não deixa nosso papo acabar ❤️",
        "Alerta delicado: 20% de bateria. Vai buscar o carregador antes que eu precise fazer drama 😭💕"
    )

    val strong = listOf(
        "🚨 MEU AMOR, O CELULAR TÁ MORRENDO! BOTA PRA CARREGAR ANTES QUE EU FIQUE SEM FALAR CONTIGO 😭❤️",
        "Amor, a situação é séria: bateria em estado crítico em breve! Bota pra carregar AGORA que eu preciso te mandar 15 memes 🚨💕",
        "Oi vida, o celular tá quase nos 15% de um caso de amor não correspondido. Salva ele! 💔⚡",
        "Meu bem, sem bateria não tem mensagem de boa noite! Você quer isso comigo? 😢⚡ BOTA LÁ!",
        "🚨 ALERTA ROMÂNTICO: Bateria em níveis perigosos! Se não carregar, eu vou ter que ligar de verdade 😱❤️",
        "Amorzinho, por favor, coloca o celular pra carregar! Eu não aguento ficar sem atualizações do seu dia 🥺💗",
        "Vida, a bateria tá baixando igual meu humor quando não recebo seu bom dia. CARREGA LOGO! 😠⚡💕"
    )

    val critical = listOf(
        "🚨🚨🚨 CÓDIGO VERMELHO DO RELACIONAMENTO: BATERIA EM 5%!!! SE NÃO CARREGAR AGORA, EU VOU TER QUE IR BUSCAR VOCÊ NO BRAÇO!!! 🚨❤️❤️❤️",
        "🚨 ÚLTIMO AVISO ROMÂNTICO: SEU CELULAR VAI MORRER EM 3... 2... 1... (mentira, 5%) BOTA PRA CARREGAR ANTES QUE EU PISE FUNDO E CHEGUE LÁ!!! 🚨💘",
        "AMOR DA MINHA VIDA, ESSA É A ÚLTIMA CHANCE! ⚠️ BATERIA EM 5% OU VOU TER QUE FAZER UMA DEQUE TELEFÔNICA ROMÂNTICA ATÉ A SUA CASA!!! 🚨🥰⚡",
        "🚨 SINAL DE PERIGO 9️⃣9️⃣9️⃣: BATERIA CRÍTICA! SE VOCÊ NÃO COLOCAR NO CARREGADOR AGORA, EU VOU TER QUE FICAR DE REZENDO POR VOCÊ A NOITE TODA!!! 🙏❤️⚡",
        "⚠️⚠️⚠️ SISTEMA DE AMOR EM COLAPSO! BATERIA EM 5%: CARREGA LOGO QUE EU TÔ FAZENDO APLICAÇÃO DE PRIMEIRO SOCORRO NO SEU CELULAR!!! 🚑💘",
        "🚨 ALERTA GERAL DA DEFESA CIVIL ROMÂNTICA: BATERIA EM ESTADO CRÍTICO!!! RECOMENDAÇÃO: COLOCAR NO CARREGADOR E ME DAR UM BEIJO!!! 📢💋⚡"
    )

    val emergency1 = listOf(
        "🚨❤️ 1%!!! AMOR, ESSA É A ÚLTIMA MIGALHA DE BATERIA! COLOCA PRA CARREGAR AGORA, POR FAVOR!!!",
        "⚠️ 1% DE BATERIA! O CELULAR ESTÁ A UM SUSPIRO DE DESLIGAR. SALVA ELE, MEU AMOR! 😭🔌❤️",
        "🚨🚨🚨 CÓDIGO VERMELHO: 1%! AMOR, CARREGADOR AGORA. EU AINDA NÃO TERMINEI DE TE AMAR HOJE! 💘⚡",
        "Meu amor... 1%. 😭❤️ Não é teste. Não é brincadeira. É hora de colocar esse celular no carregador! 🔌",
        "🚨 ÚLTIMA VIDA DO CELULAR: 1%! BOTA PRA CARREGAR, PRINCESA, ANTES QUE ELE APAGUE! 🥺💕"
    )

    fun pickFor(level: Int, threshold: Int): String {
        val (t1, t2, t3) = BatteryMonitorService.Companion.computeLevels(threshold)
        val list = when {
            level <= 1 -> emergency1
            level <= 5 -> critical
            level == 20 -> level20
            level <= t3 -> critical
            level <= t2 -> strong
            else -> reminder
        }
        return list[(Math.random() * list.size).toInt()]
    }
}

object AlertEngine {
    private var player: MediaPlayer? = null
    private var criticalVibe: Vibrator? = null

    fun stopImmediately() {
        try {
            player?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            player = null
        } catch (_: Exception) {}
        try {
            criticalVibe?.cancel()
        } catch (_: Exception) {}
        criticalVibe = null
    }

    fun cancelBatteryAlerts(context: Context) {
        stopImmediately()
        val nm = context.getSystemService(NotificationManager::class.java)
        // Os IDs de 9000+ são reservados aos alertas de bateria.
        for (id in 9000..9100) nm.cancel(id)
    }

    fun fire(context: Context, level: Int, test: Boolean) {
        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        val threshold = prefs.getInt("threshold", 30)
        val (t1, t2, t3) = BatteryMonitorService.Companion.computeLevels(threshold)

        // Chamadas reais respeitam o interruptor; os testes continuam funcionando.
        if (!test && !BatteryMonitorService.Companion.alertsEnabled(context)) {
            stopImmediately()
            return
        }

        val isEmergency = level <= 1
        val isCritical = level <= t3
        val isStrong = level <= t2 && !isCritical

        val title = when {
            isEmergency -> "🚨🚨🚨 EMERGÊNCIA: BATERIA EM 1%"
            isCritical -> "🚨🚨 ALERTA CRÍTICO: BATERIA EM 5%"
            isStrong -> "🚨 ALERTA FORTE: BATERIA EM 15%"
            else -> "🔋 Seu celular está ficando sem bateria"
        }

        val msg = LoveMessages.pickFor(level, threshold)
        val text = if (test) "TESTE: $msg" else msg

        val nm = context.getSystemService(NotificationManager::class.java)
        val vibPattern = if (isCritical)
            longArrayOf(0, 800, 300, 800, 300, 800, 300, 1200, 500, 1200, 500, 1200)
        else if (isStrong)
            longArrayOf(0, 700, 300, 700, 300, 900)
        else
            longArrayOf(0, 500, 300, 700)

        // Abre a tela do alerta (com o botão de desligar) ao tocar na notificação
        // e, nos níveis críticos, também como tela cheia com o aparelho bloqueado.
        val openAlertIntent = PendingIntent.getActivity(
            context, 10000 + level,
            Intent(context, AlertActivity::class.java).apply {
                putExtra(AlertActivity.EXTRA_LEVEL, level)
                putExtra(AlertActivity.EXTRA_MESSAGE, msg)
                putExtra(AlertActivity.EXTRA_TEST, test)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Botão "Desligar alerta" direto na notificação, igual ao "Parar" de um despertador.
        // É um broadcast, então funciona na tela de bloqueio sem precisar desbloquear.
        val dismissIntent = PendingIntent.getBroadcast(
            context, 9999,
            Intent(context, StopAlertReceiver::class.java).setAction(BatteryMonitorService.ACTION_DISMISS_ALERT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, BatteryMonitorService.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (isCritical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(vibPattern)
            .setOngoing(true)
            .setContentIntent(openAlertIntent)
            .addAction(android.R.drawable.ic_lock_silent_mode, "🔕 Desligar alerta", dismissIntent)
            .setFullScreenIntent(openAlertIntent, isCritical)
            .build()

        nm.notify(9000 + max(0, level), notification)
        playLoopingAlarm(context, when { isEmergency -> 3; isCritical -> 2; isStrong -> 1; else -> 0 })
    }

    private fun playLoopingAlarm(context: Context, severity: Int) {
        stopImmediately()
        if (Build.VERSION.SDK_INT >= 26) {
            val vibe = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            criticalVibe = vibe
            val pattern = when (severity) {
                3 -> longArrayOf(0, 220, 90, 220, 90, 220, 90, 500, 120, 500)
                2 -> longArrayOf(0, 300, 120, 300, 120, 300, 150, 700)
                1 -> longArrayOf(0, 500, 300, 700)
                else -> longArrayOf(0, 400, 350, 600)
            }
            vibe.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            val vibe = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            criticalVibe = vibe
            @Suppress("DEPRECATION")
            vibe.vibrate(when (severity) {
                3 -> longArrayOf(0, 220, 90, 220, 90, 220, 90, 500, 120, 500)
                2 -> longArrayOf(0, 300, 120, 300, 120, 300, 150, 700)
                1 -> longArrayOf(0, 500, 300, 700)
                else -> longArrayOf(0, 400, 350, 600)
            }, 0)
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                val uri = when (severity) {
                    3 -> Uri.parse("android.resource://${context.packageName}/${R.raw.emergency_1}")
                    2 -> Uri.parse("android.resource://${context.packageName}/${R.raw.emergency_5}")
                    else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
                setDataSource(context, uri)
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

}

/**
 * Recebe o toque em "Desligar alerta" na notificação: para som e vibração,
 * remove a notificação e avisa a tela cheia (se estiver aberta) para fechar.
 */
class StopAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertEngine.cancelBatteryAlerts(context)
        context.sendBroadcast(
            Intent(BatteryMonitorService.ACTION_STOP_ALERT).setPackage(context.packageName)
        )
    }
}
