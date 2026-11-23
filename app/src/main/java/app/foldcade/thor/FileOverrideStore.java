/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

import android.util.AtomicFile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** App-private, no-backup, atomic + fsynced before acknowledgement. */
final class FileOverrideStore implements GameOverrides.Store {
    private final AtomicFile file;
    FileOverrideStore(File directory) { file = new AtomicFile(new File(directory, "game-fan")); }
    @Override public GameOverrides.Override load() throws Failure {
        try (DataInputStream in = new DataInputStream(file.openRead())) {
            int version = in.readInt();
            Commands.Fan tier = Commands.Fan.request(in.readUTF());
            Commands.Fan baseline = Commands.Fan.parse(in.readUTF());
            boolean running = in.readBoolean();
            if (version != 1 || tier == null || baseline == null) throw new IOException("Invalid override record");
            return new GameOverrides.Override(tier, baseline, running);
        } catch (FileNotFoundException e) { return null; }
        catch (IOException e) { throw new Failure("internal", "Cannot read saved game override"); }
    }
    @Override public void save(GameOverrides.Override value) throws Failure {
        if (value == null) {
            file.delete();
            if (file.getBaseFile().exists()) throw new Failure("internal", "Cannot clear saved game override");
            return;
        }
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            DataOutputStream out = new DataOutputStream(stream);
            out.writeInt(1); out.writeUTF(value.tier().wire); out.writeUTF(value.baseline().wire);
            out.writeBoolean(value.appwatchWasRunning()); out.flush();
            // AtomicFile.finishWrite also syncs, but throws no error if sync fails.
            stream.getFD().sync();
            file.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) file.failWrite(stream);
            throw new Failure("internal", "Cannot save game override");
        }
    }
}
