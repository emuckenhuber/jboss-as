package org.jboss.as.cli.handlers;

import java.io.File;
import java.io.FileInputStream;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.ModelNodeFormatter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;

public class PatchHandler extends BaseOperationCommand {

    private final ArgumentWithoutValue path;

    public PatchHandler(final CommandContext context) {
        super(context, "patch", true);

        final FilenameTabCompleter pathCompleter = Util.isWindows() ? new WindowsFilenameTabCompleter(context) : new DefaultFilenameTabCompleter(context);
        path = new FileSystemPathArgument(this, pathCompleter, 0, "--path");
    }

    @Override
    protected ModelNode buildRequestWithoutHeaders(final CommandContext ctx) throws CommandFormatException {


        final ModelNode request = new ModelNode();
        request.get(Util.OPERATION).set("patch");
        request.get(Util.ADDRESS).add("core-service", "patching");
        return request;
    }

    protected byte[] readBytes(File f) throws OperationFormatException {
        byte[] bytes;
        FileInputStream is = null;
        try {
            is = new FileInputStream(f);
            bytes = new byte[(int) f.length()];
            int read = is.read(bytes);
            if(read != bytes.length) {
                throw new OperationFormatException("Failed to read bytes from " + f.getAbsolutePath() + ": " + read + " from " + f.length());
            }
        } catch (Exception e) {
            throw new OperationFormatException("Failed to read file " + f.getAbsolutePath(), e);
        } finally {
            StreamUtils.safeClose(is);
        }
        return bytes;
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        ParsedCommandLine args = ctx.getParsedCommandLine();

        final String path = this.path.getValue(args, true);

        final File f = new File(path);
        if(!f.exists()) {
            // i18n?
            throw new CommandFormatException("Path " + f.getAbsolutePath() + " doesn't exist.");
        }
        if(f.isDirectory()) {
            throw new CommandFormatException(f.getAbsolutePath() + " is a directory.");
        }

        ModelNode request = buildRequest(ctx);

        execute(ctx, request, f, false);
    }

    protected void execute(CommandContext ctx, ModelNode request, File f, boolean unmanaged) throws CommandFormatException {

        addHeaders(ctx, request);

        ModelNode result;
        try {
            if(!unmanaged) {
                OperationBuilder op = new OperationBuilder(request);
                op.addFileAsAttachment(f);
                request.get(Util.CONTENT).get(0).get(Util.INPUT_STREAM_INDEX).set(0);
                Operation operation = op.build();
                result = ctx.getModelControllerClient().execute(operation);
                operation.close();
            } else {
                result = ctx.getModelControllerClient().execute(request);
            }
        } catch (Exception e) {
            throw new CommandFormatException("Failed to add the deployment content to the repository: " + e.getLocalizedMessage());
        }
        if (!Util.isSuccess(result)) {
            throw new CommandFormatException(Util.getFailureDescription(result));
        }
        ctx.printLine(result.toJSONString(false));
    }
}
