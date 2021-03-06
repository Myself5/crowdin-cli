package com.crowdin.cli.commands.actions;

import com.crowdin.cli.client.Client;
import com.crowdin.cli.client.ProjectBuilder;
import com.crowdin.cli.client.exceptions.ResponseException;
import com.crowdin.cli.commands.functionality.FilesInterface;
import com.crowdin.cli.properties.PropertiesBean;
import com.crowdin.cli.properties.PropertiesBeanBuilder;
import com.crowdin.cli.properties.helper.FileHelperTest;
import com.crowdin.cli.properties.helper.TempProject;
import com.crowdin.cli.utils.Utils;
import com.crowdin.client.translations.model.CrowdinTranslationCreateProjectBuildForm;
import com.crowdin.client.translations.model.ProjectBuild;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class DownloadActionTest {

    static TempProject project;

    @BeforeEach
    public void createProj() {
        project = new TempProject(FileHelperTest.class);
    }

    @AfterEach
    public void deleteProj() {
        project.delete();
    }

    public static ProjectBuild buildProjectBuild(Long buildId, Long projectId, String status, Integer progress) {
        return new ProjectBuild() {{
            setId(buildId);
            setProjectId(projectId);
            setStatus(status);
            setProgress(progress);
        }};
    }

    @Test
    public void testEmptyProject() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId())).build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
            .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        AtomicReference<File> zipArchive = new AtomicReference<>();
        AtomicReference<File> tempDir = new AtomicReference<>();
        when(files.extractZipArchive(any(), any()))
            .thenAnswer((invocation -> {
                zipArchive.set(invocation.getArgument(0));
                tempDir.set(invocation.getArgument(1));
                System.out.println(tempDir.get().getAbsolutePath());
                return new ArrayList<>();
            }));

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        action.act(pb, client);

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verify(files).extractZipArchive(any(), any());
        verify(files).deleteFile(eq(zipArchive.get()));
        verify(files).deleteDirectory(tempDir.get());
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
                .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
                .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%").build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
            .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        AtomicReference<File> zipArchive = new AtomicReference<>();
        AtomicReference<File> tempDir = new AtomicReference<>();
        when(files.extractZipArchive(any(), any()))
            .thenAnswer((invocation -> {
                zipArchive.set(invocation.getArgument(0));
                tempDir.set(invocation.getArgument(1));
                return new ArrayList<File>() {{
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"));
                }};
            }));

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        action.act(pb, client);

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verify(files).extractZipArchive(any(), any());
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"),
            new File(pb.getBasePath() + "first.po-CR-ru-RU"));
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"),
            new File(pb.getBasePath() + "first.po-CR-uk-UA"));
        verify(files).deleteFile(eq(zipArchive.get()));
        verify(files).deleteDirectory(tempDir.get());
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile_WithExportApprovedOnly_WithSkipUntranslatedFiles() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
                .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
                .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
                .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                        .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%").build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm() {{
            setExportApprovedOnly(true);
            setSkipUntranslatedFiles(true);
        }};
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
                .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
                .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        AtomicReference<File> zipArchive = new AtomicReference<>();
        AtomicReference<File> tempDir = new AtomicReference<>();
        when(files.extractZipArchive(any(), any()))
                .thenAnswer((invocation -> {
                    zipArchive.set(invocation.getArgument(0));
                    tempDir.set(invocation.getArgument(1));
                    return new ArrayList<File>() {{
                        add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"));
                        add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"));
                    }};
                }));

        Action action = new DownloadAction(files, false, null, null, false, false, null, true, true);
        action.act(pb, client);

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verify(files).extractZipArchive(any(), any());
        verify(files).copyFile(
                new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"),
                new File(pb.getBasePath() + "first.po-CR-ru-RU"));
        verify(files).copyFile(
                new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"),
                new File(pb.getBasePath() + "first.po-CR-uk-UA"));
        verify(files).deleteFile(eq(zipArchive.get()));
        verify(files).deleteDirectory(tempDir.get());
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile_LongBuild() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%").build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "building", 25));
        when(client.checkBuildingTranslation(eq(buildId)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "building", 50))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "building", 75))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
            .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        AtomicReference<File> zipArchive = new AtomicReference<>();
        AtomicReference<File> tempDir = new AtomicReference<>();
        when(files.extractZipArchive(any(), any()))
            .thenAnswer((invocation -> {
                zipArchive.set(invocation.getArgument(0));
                tempDir.set(invocation.getArgument(1));
                return new ArrayList<File>() {{
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"));
                }};
            }));

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        action.act(pb, client);

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client, times(3)).checkBuildingTranslation(eq(buildId));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verify(files).extractZipArchive(any(), any());
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"),
            new File(pb.getBasePath() + "first.po-CR-ru-RU"));
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"),
            new File(pb.getBasePath() + "first.po-CR-uk-UA"));
        verify(files).deleteFile(eq(zipArchive.get()));
        verify(files).deleteDirectory(tempDir.get());
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingOneUnfittingFile_LongBuild() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
                .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
                .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%")
                .addFile("second.po", "gettext", 102L, null, null, "/%original_file_name%-CR-%locale%")
                .build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
            .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        AtomicReference<File> zipArchive = new AtomicReference<>();
        AtomicReference<File> tempDir = new AtomicReference<>();
        when(files.extractZipArchive(any(), any()))
            .thenAnswer((invocation -> {
                zipArchive.set(invocation.getArgument(0));
                tempDir.set(invocation.getArgument(1));
                return new ArrayList<File>() {{
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "second.po-CR-uk-UA"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "second.po-CR-ru-RU"));
                }};
            }));

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        action.act(pb, client);

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verify(files).extractZipArchive(any(), any());
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"),
            new File(pb.getBasePath() + "first.po-CR-ru-RU"));
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"),
            new File(pb.getBasePath() + "first.po-CR-uk-UA"));
        verify(files).deleteFile(eq(zipArchive.get()));
        verify(files).deleteDirectory(tempDir.get());
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingOneUnfittingOneWithUnfoundSourceFile_LongBuild() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%")
                .addFile("second.po", "gettext", 102L, null, null, "/%original_file_name%-CR-%locale%")
                .build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
            .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        AtomicReference<File> zipArchive = new AtomicReference<>();
        AtomicReference<File> tempDir = new AtomicReference<>();
        when(files.extractZipArchive(any(), any()))
            .thenAnswer((invocation -> {
                zipArchive.set(invocation.getArgument(0));
                tempDir.set(invocation.getArgument(1));
                return new ArrayList<File>() {{
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "second.po-CR-uk-UA"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "second.po-CR-ru-RU"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "third.po-CR-uk-UA"));
                }};
            }));

        Action action = new DownloadAction(files, false, null, null, false, true, null, null, null);
        action.act(pb, client);

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verify(files).extractZipArchive(any(), any());
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"),
            new File(pb.getBasePath() + "first.po-CR-ru-RU"));
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"),
            new File(pb.getBasePath() + "first.po-CR-uk-UA"));
        verify(files).deleteFile(eq(zipArchive.get()));
        verify(files).deleteDirectory(tempDir.get());
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile_WithLanguageMapping() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%")
                .addFile("second.po", "gettext", 102L, null, null, "/%original_file_name%-CR-%locale%")
                .addLanguageMapping("ua", "locale", "UA")
                .build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
                .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
                .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        AtomicReference<File> zipArchive = new AtomicReference<>();
        AtomicReference<File> tempDir = new AtomicReference<>();
        when(files.extractZipArchive(any(), any()))
                .thenAnswer((invocation -> {
                    zipArchive.set(invocation.getArgument(0));
                    tempDir.set(invocation.getArgument(1));
                    return new ArrayList<File>() {{
                        add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-UA"));
                        add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"));
                        add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "second.po-CR-UA"));
                        add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "second.po-CR-ru-RU"));
                        add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "third.po-CR-UA"));
                    }};
                }));

        Action action = new DownloadAction(files, false, null, null, false, true, null, null, null);
        action.act(pb, client);

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verify(files).extractZipArchive(any(), any());
        verify(files).copyFile(
                new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"),
                new File(pb.getBasePath() + "first.po-CR-ru-RU"));
        verify(files).copyFile(
                new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-UA"),
                new File(pb.getBasePath() + "first.po-CR-UA"));
        verify(files).deleteFile(eq(zipArchive.get()));
        verify(files).deleteDirectory(tempDir.get());
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile_FailBuild() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%").build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenThrow(new RuntimeException());
        when(client.downloadBuild(eq(buildId)))
            .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        assertThrows(RuntimeException.class, () -> action.act(pb, client));

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verifyNoMoreInteractions(client);

        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile_failDownloadProject() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenThrow(new RuntimeException());

        FilesInterface files = mock(FilesInterface.class);

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        assertThrows(RuntimeException.class, () -> action.act(pb, client));

        verify(client).downloadFullProject();
        verifyNoMoreInteractions(client);

        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile_failDeleteFile() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%").build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
            .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        AtomicReference<File> zipArchive = new AtomicReference<>();
        AtomicReference<File> tempDir = new AtomicReference<>();
        when(files.extractZipArchive(any(), any()))
            .thenAnswer((invocation -> {
                zipArchive.set(invocation.getArgument(0));
                tempDir.set(invocation.getArgument(1));
                return new ArrayList<File>() {{
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"));
                    add(new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"));
                }};
            }));
        doThrow(IOException.class)
            .when(files).deleteFile(any());

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        assertThrows(RuntimeException.class, () -> action.act(pb, client));

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verify(files).extractZipArchive(any(), any());
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-ru-RU"),
            new File(pb.getBasePath() + "first.po-CR-ru-RU"));
        verify(files).copyFile(
            new File(tempDir.get().getAbsolutePath() + Utils.PATH_SEPARATOR + "first.po-CR-uk-UA"),
            new File(pb.getBasePath() + "first.po-CR-uk-UA"));
        verify(files).deleteFile(eq(zipArchive.get()));
        verify(files).deleteDirectory(tempDir.get());
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile_failDownloadingException() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
                .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
                .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
                .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                        .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%").build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
            .thenThrow(new RuntimeException());

        FilesInterface files = mock(FilesInterface.class);

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        assertThrows(RuntimeException.class, () -> action.act(pb, client));

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verifyNoMoreInteractions(files);
    }

    @Test
    public void testProjectOneFittingFile_failWritingFile() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        project.addFile("first.po");

        Client client = mock(Client.class);
        when(client.downloadFullProject())
            .thenReturn(ProjectBuilder.emptyProject(Long.parseLong(pb.getProjectId()))
                .addFile("first.po", "gettext", 101L, null, null, "/%original_file_name%-CR-%locale%").build());
        CrowdinTranslationCreateProjectBuildForm buildProjectTranslationRequest = new CrowdinTranslationCreateProjectBuildForm();
        long buildId = 42L;
        InputStream zipArchiveData = IOUtils.toInputStream("not-really-zip-archive", "UTF-8");
        when(client.startBuildingTranslation(eq(buildProjectTranslationRequest)))
            .thenReturn(buildProjectBuild(buildId, Long.parseLong(pb.getProjectId()), "finished", 100));
        when(client.downloadBuild(eq(buildId)))
            .thenReturn(zipArchiveData);

        FilesInterface files = mock(FilesInterface.class);
        doThrow(IOException.class)
            .when(files)
                .writeToFile(any(), any());

        Action action = new DownloadAction(files, false, null, null, false, false, null, null, null);
        assertThrows(RuntimeException.class, () -> action.act(pb, client));

        verify(client).downloadFullProject();
        verify(client).startBuildingTranslation(eq(buildProjectTranslationRequest));
        verify(client).downloadBuild(eq(buildId));
        verifyNoMoreInteractions(client);

        verify(files).writeToFile(any(), eq(zipArchiveData));
        verifyNoMoreInteractions(files);
    }

    @Test
    public void testSkipUnTranslatedFilesSources_fail() throws ResponseException, IOException {
        PropertiesBeanBuilder pbBuilder = PropertiesBeanBuilder
            .minimalBuiltPropertiesBean("*", Utils.PATH_SEPARATOR + "%original_file_name%-CR-%locale%")
            .setBasePath(project.getBasePath());
        PropertiesBean pb = pbBuilder.build();

        Client client = mock(Client.class);

        FilesInterface files = mock(FilesInterface.class);

        Action action = new DownloadAction(files, false, null, null, false, false, true, true, null);
        assertThrows(RuntimeException.class, () -> action.act(pb, client));

        verifyZeroInteractions(client);
        verifyZeroInteractions(files);
    }
}
