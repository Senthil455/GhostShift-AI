package ai.ghostshift.api;

import ai.ghostshift.domain.RegisterDirectoryRequest;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.domain.ScanRequest;
import ai.ghostshift.domain.ScanResult;
import ai.ghostshift.service.DirectoryService;
import ai.ghostshift.service.FileScanner;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;

import java.util.List;

@Controller("/api/v1/directories")
public class DirectoryController {
    private final DirectoryService directories;
    private final FileScanner scanner;

    public DirectoryController(DirectoryService directories, FileScanner scanner) {
        this.directories = directories;
        this.scanner = scanner;
    }

    @Get
    public List<RegisteredDirectory> list() {
        return directories.list();
    }

    @Post
    public HttpResponse<RegisteredDirectory> register(@Body RegisterDirectoryRequest request) {
        RegisteredDirectory directory = directories.register(request.path(), request.displayName(),
                request.directoryType(), request.allowOrganize());
        return HttpResponse.created(directory);
    }

    @Get("/{id}")
    public RegisteredDirectory get(@PathVariable long id) {
        return directories.get(id);
    }

    @Post("/{id}/scan")
    public ScanResult scan(@PathVariable long id, @Body ScanRequest request) {
        RegisteredDirectory directory = directories.get(id);
        directories.requireRead(id);
        boolean recursive = request != null && Boolean.TRUE.equals(request.recursive());
        FileScanner.Scan scan = scanner.scan(directory, recursive, 0);
        return scanner.toResult(directory, scan);
    }

    @Delete("/{id}")
    public HttpResponse<Void> remove(@PathVariable long id) {
        directories.remove(id);
        return HttpResponse.noContent();
    }
}
