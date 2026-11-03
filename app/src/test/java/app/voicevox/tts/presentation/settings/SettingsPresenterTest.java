package app.voicevox.tts.presentation.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.voicevox.tts.application.fake.Fakes;
import app.voicevox.tts.application.voice.VoicePackService;
import app.voicevox.tts.application.voice.VoiceSelectionService;
import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;
import app.voicevox.tts.domain.voice.VoiceNames;
import app.voicevox.tts.domain.voice.VoiceStyle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * The settings screen's use cases, driven through fakes.
 *
 * <p>Every assertion here is about a decision the presenter makes — whether a tap downloads
 * first, whether a second download is refused, whether a delete stops the audio that is
 * currently playing. None of it needs an emulator.
 */
public class SettingsPresenterTest {
    private Fakes.Packs packs;
    private Fakes.Engine engine;
    private Fakes.Player player;
    private Fakes.Defaults defaults;
    private ScreenFakes.Screen screen;
    private ScreenFakes.Host host;
    private SettingsPresenter presenter;

    @BeforeEach
    public void setUp() {
        packs = new Fakes.Packs();
        // 0.vvm ships inside the APK, so a fresh install always starts with one pack.
        packs.installed.add("0.vvm");
        engine = new Fakes.Engine();
        player = new Fakes.Player();
        defaults = new Fakes.Defaults();
        screen = new ScreenFakes.Screen();
        host = new ScreenFakes.Host();
        // Pinned to Chinese so no assertion depends on the machine running the tests.
        presenter = presenter(VoiceNames.CHINESE);
        presenter.start();
    }

    /** The real use cases over fake ports, as {@code di.AppContainer} wires them. */
    private SettingsPresenter presenter(String language) {
        return new SettingsPresenter(host, new VoicePackService(packs, packs, engine),
                new VoiceSelectionService(packs, defaults), engine, player, screen, language);
    }

    // ------------------------------------------------------------------ first paint

    @Test
    public void startRendersEveryPackAndCountsTheInstalledOnes() {
        assertEquals(25, screen.renderedPacks.size());
        assertEquals(1, screen.renderedInstalled.size());
        assertTrue(screen.renderedInstalled.contains("0.vvm"));
        assertEquals("1 / 25", screen.summary);
        assertTrue(screen.summaryDetail.startsWith("占用 "));
        assertTrue(screen.engineInfo.contains("系统已识别本引擎"));
    }

    @Test
    public void aMissingDictionaryIsReportedBeforeEngineRegistration() {
        packs.dictionaryReady = false;
        presenter.refresh();
        assertTrue(screen.engineInfo.startsWith("词典缺失"));
    }

    @Test
    public void anEngineTheSystemDoesNotListIsReportedAsMissing() {
        host.registered = false;
        presenter.refresh();
        assertTrue(screen.engineInfo.contains("系统未识别本引擎"));
    }

    @Test
    public void residentModelsAreMentionedOnlyWhenTheCoreIsUp() {
        engine.ready = false;
        engine.loaded = 2;
        presenter.refresh();
        assertFalse(screen.summaryDetail.contains("内存驻留"));

        engine.ready = true;
        presenter.refresh();
        assertTrue(screen.summaryDetail.contains("内存驻留 2 个"));
    }

    // ------------------------------------------------------------------ default voice

    @Test
    public void withoutAChoiceTheFirstInstalledCharacterIsInEffect() {
        assertEquals(4, screen.defaultChoices.size());
        assertEquals(2, screen.defaultEffective);
        assertEquals(-1, defaults.styleId());
    }

    @Test
    public void choosingADefaultVoiceStoresItAndMovesTheHighlight() {
        presenter.onDefaultVoiceRequested(VoiceCatalog.byStyleId(3));

        assertEquals(3, defaults.styleId());
        assertEquals(1, defaults.writes);
        assertEquals(3, screen.defaultEffective);
        assertTrue(screen.lastMessage().contains("毛豆泥"));
    }

    @Test
    public void anEnglishPhoneReadsTheNamesInEnglish() {
        SettingsPresenter english = presenter(VoiceNames.ENGLISH);

        english.onDefaultVoiceRequested(VoiceCatalog.byStyleId(3));

        assertTrue(screen.lastMessage().contains("Zundamon / Normal"));
    }

    @Test
    public void aChoiceWhosePackIsGoneFallsBackToWhatCanSpeak() {
        // 冥鳴ひまり lives in 1.vvm, which is not on disk.
        defaults.styleId = 14;
        presenter.refresh();

        assertEquals(2, screen.defaultEffective);
    }

    @Test
    public void withNothingInstalledThereIsNoDefaultVoice() {
        packs.installed.clear();
        presenter.refresh();

        assertTrue(screen.defaultChoices.isEmpty());
        assertEquals(-1, screen.defaultEffective);
    }

    // ------------------------------------------------------------------ player

    @Test
    public void previewingAnInstalledPackPlaysWithoutDownloading() {
        presenter.onPreviewRequested(VoiceCatalog.byFile("0.vvm"), VoiceCatalog.byStyleId(3));

        assertTrue(packs.downloaded.isEmpty());
        assertEquals(1, player.toggles.size());
        assertEquals(3, screen.playing);
        assertEquals(Tone.INFO, screen.lastTone());
        assertTrue(screen.said("正在准备"));
    }

    @Test
    public void previewingAMissingPackDownloadsItFirstThenPlays() {
        VoicePack pack = VoiceCatalog.packOfStyle(14);
        presenter.onPreviewRequested(pack, VoiceCatalog.byStyleId(14));

        assertEquals(List.of("1.vvm"), packs.downloaded);
        assertTrue(packs.installed.contains("1.vvm"));
        assertEquals(1, player.toggles.size());
        assertEquals(14, player.playing);
        assertTrue(screen.messages.get(0).contains("还没下载"));
        assertTrue(screen.said("下载完成"));
        // The new pack has to be on screen before it can be highlighted.
        assertTrue(screen.renderedInstalled.contains("1.vvm"));
    }

    @Test
    public void tappingTheSameChipTwiceStopsPlayback() {
        VoicePack pack = VoiceCatalog.byFile("0.vvm");
        VoiceStyle style = VoiceCatalog.byStyleId(2);

        presenter.onPreviewRequested(pack, style);
        assertEquals(2, screen.playing);

        presenter.onPreviewRequested(pack, style);
        assertEquals(-1, screen.playing);
        assertEquals("已停止试听", screen.lastMessage());
    }

    @Test
    public void aDownloadedPackRebindsTheEngineSoTheNewVoiceAppears() {
        presenter.onPreviewRequested(VoiceCatalog.packOfStyle(14), VoiceCatalog.byStyleId(14));

        // The engine builds its voice list from disk: without this the character stays
        // invisible to every other app until the process restarts.
        assertTrue(player.releaseCount >= 1);
        assertTrue(player.stopCount >= 1);
    }

    @Test
    public void playerCallbacksArePhrasedByThePresenter() {
        VoiceStyle style = VoiceCatalog.byStyleId(3);

        player.playing = 3;
        presenter.onStarted(style);
        assertEquals(3, screen.playing);
        assertTrue(screen.lastMessage().contains(style.displayName(VoiceNames.CHINESE)));
        assertEquals(Tone.OK, screen.lastTone());

        player.playing = -1;
        presenter.onFinished(style);
        assertEquals(-1, screen.playing);
        assertEquals(Tone.NEUTRAL, screen.lastTone());

        presenter.onFailure(style, "codec");
        assertEquals(Tone.ERROR, screen.lastTone());
        assertTrue(screen.lastMessage().contains("codec"));
    }

    @Test
    public void anUnknownStyleIsIgnoredRatherThanCrashing() {
        presenter.onPreviewRequested(null, VoiceCatalog.byStyleId(3));
        assertTrue(player.toggles.isEmpty());
        assertTrue(packs.downloaded.isEmpty());
    }

    // ------------------------------------------------------------------ download

    @Test
    public void aSecondDownloadIsRejectedWhileOneRuns() {
        VoicePack first = VoiceCatalog.packOfStyle(14);
        VoicePack second = VoiceCatalog.packOfStyle(16);
        packs.duringDownload = () -> presenter.onDownloadRequested(second);

        presenter.onDownloadRequested(first);

        assertEquals(List.of("1.vvm"), packs.downloaded);
        assertTrue(screen.said("正在下载 1.vvm，请稍候"));
        assertFalse(packs.installed.contains("2.vvm"));
    }

    @Test
    public void progressIsReportedPerCard() {
        presenter.onDownloadRequested(VoiceCatalog.packOfStyle(14));

        assertEquals("1.vvm:true:0", screen.progressCalls.get(0));
        assertEquals("1.vvm:true:100", screen.progressCalls.get(screen.progressCalls.size() - 1));
        assertEquals(Integer.valueOf(100), screen.progressPercent.get("1.vvm"));
    }

    @Test
    public void aFailedDownloadReportsTheReasonAndReEnablesTheCard() {
        packs.failDownloadWith = new IOException("no network");
        presenter.onDownloadRequested(VoiceCatalog.packOfStyle(14));

        assertEquals(Tone.ERROR, screen.lastTone());
        assertTrue(screen.lastMessage().startsWith("下载失败：no network"));
        assertTrue(screen.progressCalls.get(screen.progressCalls.size() - 1).endsWith(":false:0"));
        assertFalse(packs.installed.contains("1.vvm"));
    }

    @Test
    public void aFailedDownloadLeavesThePresenterFreeToTryAgain() {
        packs.failDownloadWith = new IOException("no network");
        presenter.onDownloadRequested(VoiceCatalog.packOfStyle(14));

        packs.failDownloadWith = null;
        presenter.onDownloadRequested(VoiceCatalog.packOfStyle(14));

        assertEquals(List.of("1.vvm", "1.vvm"), packs.downloaded);
        assertTrue(packs.installed.contains("1.vvm"));
    }

    // ------------------------------------------------------------------ delete

    @Test
    public void deleteAsksBeforeRemovingAnything() {
        host.autoConfirm = false;
        presenter.onDeleteRequested(VoiceCatalog.byFile("0.vvm"));

        assertEquals(1, host.confirmations);
        assertTrue(host.confirmTitle.contains("四国玫碳"));
        assertTrue(host.confirmMessage.contains("MB"));
        assertFalse(packs.deleteCalled);
        assertTrue(packs.installed.contains("0.vvm"));
    }

    @Test
    public void deletingTheLastPackWarnsThatSpeechGoesSilent() {
        presenter.onDeleteRequested(VoiceCatalog.byFile("0.vvm"));

        assertTrue(packs.deleteCalled);
        assertEquals(Tone.WARN, screen.lastTone());
        assertTrue(screen.lastMessage().contains("朗读会没有声音"));
    }

    @Test
    public void deletingOneOfSeveralPacksStaysOptimistic() {
        packs.installed.add("1.vvm");
        presenter.onDeleteRequested(VoiceCatalog.byFile("0.vvm"));

        assertEquals(Tone.OK, screen.lastTone());
        assertTrue(screen.lastMessage().contains("随时可以重新下载"));
        assertEquals("1 / 25", screen.summary);
    }

    @Test
    public void deletingThePackThatIsSpeakingStopsIt() {
        presenter.onPreviewRequested(VoiceCatalog.byFile("0.vvm"), VoiceCatalog.byStyleId(2));
        assertEquals(2, player.playing);

        presenter.delete(VoiceCatalog.byFile("0.vvm"));

        assertEquals(-1, player.playing);
        assertEquals(1, player.stopCount);
        assertTrue(player.releaseCount >= 1);
    }

    @Test
    public void deletingAnotherPackLeavesPlaybackAlone() {
        packs.installed.add("1.vvm");
        presenter.onPreviewRequested(VoiceCatalog.byFile("0.vvm"), VoiceCatalog.byStyleId(2));

        presenter.delete(VoiceCatalog.byFile("1.vvm"));

        assertEquals(2, player.playing);
        assertEquals(0, player.stopCount);
    }

    @Test
    public void aPreviewAfterDeletingFallsBackToDownloadingAgain() {
        presenter.delete(VoiceCatalog.byFile("0.vvm"));
        assertTrue(packs.installed.isEmpty());

        presenter.onPreviewRequested(VoiceCatalog.byFile("0.vvm"), VoiceCatalog.byStyleId(2));

        // The pack is gone from disk, so the tap has to fetch it instead of playing.
        assertEquals(List.of("0.vvm"), packs.downloaded);
        assertTrue(packs.installed.contains("0.vvm"));
        assertEquals(2, player.playing);
    }

    // ------------------------------------------------------------------ misc

    @Test
    public void aMissingSystemSettingsScreenFallsBackToText() {
        host.systemSettingsAvailable = false;
        presenter.onOpenSystemSettings();

        assertEquals(Tone.WARN, screen.lastTone());
        assertTrue(screen.lastMessage().contains("设置 → 无障碍"));
    }

    @Test
    public void anAvailableSystemSettingsScreenSaysNothing() {
        presenter.onOpenSystemSettings();
        assertNull(screen.lastMessage());
    }

    @Test
    public void stopReleasesThePreviewBinding() {
        presenter.stop();
        assertEquals(1, player.releaseCount);
    }

    // ------------------------------------------------------------------ first run

    @Test
    public void openingTheScreenUnpacksTheBundledResources() {
        // A fresh install used to unpack only once the TTS service was bound by someone.
        assertEquals(1, packs.prepareCalls);
    }

    @Test
    public void aDictionaryStillBeingUnpackedIsNotReportedAsBroken() {
        packs.dictionaryReady = false;
        host.deferBackground = true;
        presenter = presenter(VoiceNames.CHINESE);
        presenter.start();

        assertTrue(screen.engineInfo.startsWith("正在解包"));

        host.runDeferred();

        // The unpack finished and the dictionary is still missing: now it is a broken install.
        assertTrue(screen.engineInfo.startsWith("词典缺失"));
    }

    // ------------------------------------------------------------------ process-wide download

    @Test
    public void aDownloadStartedByAnEarlierScreenIsAdoptedNotRepeated() {
        // The Activity before a rotation started 1.vvm; this screen is a new presenter.
        packs.foreignDownload = "1.vvm";
        packs.foreignPercent = 40;
        host.deferBackground = true;
        presenter = presenter(VoiceNames.CHINESE);
        presenter.start();

        assertEquals(40, (int) screen.progressPercent.get("1.vvm"));
        presenter.onDownloadRequested(VoiceCatalog.byFile("1.vvm"));
        assertTrue(packs.downloaded.isEmpty(), "a second copy of the same file was started");
        assertTrue(screen.lastMessage().startsWith("正在下载 1.vvm"));

        host.runDeferred();

        assertTrue(packs.installed.contains("1.vvm"));
        assertTrue(screen.lastMessage().contains("下载完成"));
    }

    @Test
    public void aRepaintDuringADownloadKeepsTheCardBusy() {
        packs.duringDownload = () -> presenter.refresh();
        presenter.onDownloadRequested(VoiceCatalog.byFile("1.vvm"));

        // refresh() rebuilt the cards mid-download and must have re-marked 1.vvm as active.
        assertTrue(screen.progressCalls.stream().filter(c -> c.startsWith("1.vvm:true"))
                .count() >= 2);
    }

    // ------------------------------------------------------------------ delete off the UI thread

    @Test
    public void deletingRunsOnAWorkerThread() {
        host.deferBackground = true;
        presenter.delete(VoiceCatalog.byFile("0.vvm"));

        assertFalse(packs.deleteCalled, "the delete ran on the UI thread");
        assertTrue(screen.lastMessage().startsWith("正在删除"));

        host.runDeferred();

        assertTrue(packs.deleteCalled);
        assertTrue(screen.lastMessage().contains("已删除"));
    }

    @Test
    public void aSecondDeleteWhileOneIsRunningIsRefused() {
        host.deferBackground = true;
        packs.installed.add("1.vvm");
        presenter.delete(VoiceCatalog.byFile("0.vvm"));
        presenter.delete(VoiceCatalog.byFile("1.vvm"));

        assertEquals("正在删除，请稍候…", screen.lastMessage());
        host.runDeferred();
        assertTrue(packs.installed.contains("1.vvm"));
    }

    @Test
    public void aPackThatIsDownloadingCannotBeDeleted() {
        packs.duringDownload = () -> presenter.delete(VoiceCatalog.byFile("1.vvm"));
        presenter.onDownloadRequested(VoiceCatalog.byFile("1.vvm"));

        assertTrue(screen.said("正在下载，完成后再删除"));
        assertFalse(packs.deleteCalled);
    }
}
