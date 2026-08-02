package com.rs2.script;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.io.FileSystem;

/**
 * Graal filesystem limited to read-only module access beneath one content root.
 */
final class ReadOnlyContentFileSystem implements FileSystem {

	private final Path root;
	private Path currentDirectory;

	ReadOnlyContentFileSystem(Path contentRoot) {
		Path configuredRoot = contentRoot.toAbsolutePath().normalize();
		try {
			this.root = configuredRoot.toRealPath();
		} catch (IOException e) {
			throw new IllegalArgumentException("Content directory must exist: " + configuredRoot, e);
		}
		this.currentDirectory = this.root;
	}

	@Override
	public Path parsePath(URI uri) {
		return Paths.get(uri);
	}

	@Override
	public Path parsePath(String path) {
		return Paths.get(path);
	}

	@Override
	public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions)
			throws IOException {
		Path allowed = allowedPath(path);
		// Graal checks EXECUTE while resolving an ES module even though the file
		// is only opened for reading. Process creation remains disabled on the
		// Context, so only WRITE is forbidden at this filesystem boundary.
		if (modes.contains(AccessMode.WRITE)) {
			throw denied(path, "write access is not allowed: " + modes);
		}
		allowed.getFileSystem().provider().checkAccess(allowed, modes.toArray(new AccessMode[modes.size()]));
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		throw denied(dir);
	}

	@Override
	public void delete(Path path) throws IOException {
		throw denied(path);
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
			FileAttribute<?>... attrs) throws IOException {
		if (options.contains(StandardOpenOption.WRITE)
				|| options.contains(StandardOpenOption.APPEND)
				|| options.contains(StandardOpenOption.CREATE)
				|| options.contains(StandardOpenOption.CREATE_NEW)
				|| options.contains(StandardOpenOption.DELETE_ON_CLOSE)
				|| options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
			throw denied(path, "write-capable open options are not allowed: " + options);
		}
		return Files.newByteChannel(allowedPath(path), options, attrs);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir,
			DirectoryStream.Filter<? super Path> filter) throws IOException {
		return Files.newDirectoryStream(allowedPath(dir), filter);
	}

	@Override
	public Path toAbsolutePath(Path path) {
		return absolute(path);
	}

	@Override
	public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
		Path real = allowedPath(path).toRealPath(linkOptions);
		if (!real.startsWith(realRoot())) {
			throw denied(path, "resolved path is outside the content directory");
		}
		return real;
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes,
			LinkOption... options) throws IOException {
		return Files.readAttributes(allowedPath(path), attributes, options);
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value,
			LinkOption... options) throws IOException {
		throw denied(path);
	}

	@Override
	public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
		Path candidate;
		try {
			candidate = pathForAccess(currentWorkingDirectory);
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid script working directory", e);
		}
		if (!candidate.startsWith(root)) {
			throw new IllegalArgumentException("Working directory must remain inside the content root");
		}
		this.currentDirectory = candidate;
	}

	private Path allowedPath(Path path) throws IOException {
		Path candidate = pathForAccess(path);
		if (!candidate.startsWith(root)) {
			throw denied(path, "path is outside the content directory: " + candidate);
		}
		if (Files.exists(candidate)) {
			Path real = candidate.toRealPath();
			if (!real.startsWith(realRoot())) {
				throw denied(path, "symbolic link resolves outside the content directory");
			}
		}
		return candidate;
	}

	private Path pathForAccess(Path path) throws IOException {
		Path candidate = absolute(path);
		if (Files.exists(candidate)) {
			return candidate.toRealPath();
		}
		Path parent = candidate.getParent();
		if (parent != null && Files.exists(parent)) {
			return parent.toRealPath().resolve(candidate.getFileName()).normalize();
		}
		return candidate;
	}

	private Path absolute(Path path) {
		return (path.isAbsolute() ? path : currentDirectory.resolve(path)).normalize().toAbsolutePath();
	}

	private Path realRoot() throws IOException {
		return root.toRealPath();
	}

	private static AccessDeniedException denied(Path path) {
		return denied(path, "Script filesystem access is read-only and limited to the content directory");
	}

	private static AccessDeniedException denied(Path path, String reason) {
		return new AccessDeniedException(String.valueOf(path), null, reason);
	}
}
