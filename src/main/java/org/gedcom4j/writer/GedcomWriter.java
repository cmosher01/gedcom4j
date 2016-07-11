/*
 * Copyright (c) 2009-2016 Matthew R. Harrah
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package org.gedcom4j.writer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.gedcom4j.exception.GedcomWriterException;
import org.gedcom4j.exception.GedcomWriterVersionDataMismatchException;
import org.gedcom4j.exception.WriterCancelledException;
import org.gedcom4j.io.event.FileProgressEvent;
import org.gedcom4j.io.event.FileProgressListener;
import org.gedcom4j.io.writer.GedcomFileWriter;
import org.gedcom4j.model.*;
import org.gedcom4j.validate.GedcomValidationFinding;
import org.gedcom4j.validate.GedcomValidator;
import org.gedcom4j.validate.Severity;
import org.gedcom4j.writer.event.ConstructProgressEvent;
import org.gedcom4j.writer.event.ConstructProgressListener;

/**
 * <p>
 * A class for writing the gedcom structure out as a GEDCOM 5.5 compliant file.
 * </p>
 * <p>
 * General usage is as follows:
 * </p>
 * <ul>
 * <li>Instantiate a <code>GedcomWriter</code>, passing the {@link Gedcom} to be written as a parameter to the
 * constructor.</li>
 * <li>Call one of the variants of the <code>write</code> method to write the data</li>
 * <li>Optionally check the contents of the {@link #validationFindings} collection to see if there was anything
 * problematic found in your data.</li>
 * </ul>
 * 
 * <h3>Validation</h3>
 * <p>
 * By default, this class automatically validates your data prior to writing it by using a
 * {@link org.gedcom4j.validate.GedcomValidator}. This is to prevent writing data that does not conform to the spec.
 * </p>
 * 
 * <p>
 * If validation finds any errors that are of severity ERROR, the writer will throw an Exception (usually
 * {@link GedcomWriterVersionDataMismatchException} or {@link GedcomWriterException}). If this occurs, check the
 * {@link #validationFindings} collection to determine what the problem was.
 * </p>
 * 
 * <p>
 * Although validation is automatically performed, autorepair is turned off by default (see
 * {@link org.gedcom4j.validate.GedcomValidator#setAutorepairEnabled(boolean)})...this way your data is not altered.
 * Validation can be suppressed if you want by setting {@link #validationSuppressed} to true, but this is not
 * recommended. You can also force autorepair on if you want.
 * </p>
 * 
 * @author frizbog1
 */
public class GedcomWriter extends AbstractEmitter<Gedcom> {
    /**
     * The text lines of the GEDCOM file we're writing, which will be written using a {@link GedcomFileWriter}.
     * Deliberately package-private so tests can access it but others can't alter it.
     */
    List<String> lines = new ArrayList<String>();

    /**
     * Are we suppressing the call to the validator? Deliberately package-private so unit tests can fiddle with it to
     * make testing easy.
     */
    boolean validationSuppressed = false;

    /**
     * Whether or not to use autorepair in the validation step
     */
    private boolean autorepair = false;

    /**
     * Has this writer been cancelled?
     */
    private boolean cancelled;

    /**
     * Send a notification whenever more than this many lines are constructed
     */
    private int constructionNotificationRate = 500;

    /**
     * The list of observers on string construction
     */
    private final List<WeakReference<ConstructProgressListener>> constructObservers = new CopyOnWriteArrayList<WeakReference<ConstructProgressListener>>();

    /**
     * Send a notification whenever more than this many lines are written to a file
     */
    private int fileNotificationRate = 500;

    /**
     * The list of observers on file operations
     */
    private final List<WeakReference<FileProgressListener>> fileObservers = new CopyOnWriteArrayList<WeakReference<FileProgressListener>>();

    /**
     * The number of lines constructed as last reported to the observers
     */
    private int lastLineCountNotified = 0;

    /**
     * Whether to use little-endian unicode
     */
    private boolean useLittleEndianForUnicode = true;

    /**
     * A list of things found during validation of the gedcom data prior to writing it. If the data cannot be written
     * due to an exception caused by failure to validate, this collection will describe the issues encountered.
     */
    private List<GedcomValidationFinding> validationFindings;

    /**
     * Constructor
     * 
     * @param gedcom
     *            the {@link Gedcom} structure to write out
     */
    public GedcomWriter(Gedcom gedcom) {
        super(null, 0, gedcom);
        baseWriter = this;
    }

    /**
     * Cancel construction of the GEDCOM and writing it to a file
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Get the construction notification rate - how many lines need to be constructed before getting a notification
     * 
     * @return the construction notification rate - how many lines need to be constructed before getting a notification
     */
    public int getConstructionNotificationRate() {
        return constructionNotificationRate;
    }

    /**
     * Get the number of lines to be written between each file notification
     * 
     * @return the number of lines to be written between each file notification
     */
    public int getFileNotificationRate() {
        return fileNotificationRate;
    }

    /**
     * Get the validationFindings
     * 
     * @return the validationFindings
     */
    public List<GedcomValidationFinding> getValidationFindings() {
        return validationFindings;
    }

    /**
     * Get the autorepair
     * 
     * @return the autorepair
     */
    public boolean isAutorepair() {
        return autorepair;
    }

    /**
     * Has this writer been cancelled?
     * 
     * @return true if this writer has been cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Get the useLittleEndianForUnicode
     * 
     * @return the useLittleEndianForUnicode
     */
    public boolean isUseLittleEndianForUnicode() {
        return useLittleEndianForUnicode;
    }

    /**
     * Notify all listeners about the file progress
     * 
     * @param e
     *            the change event to tell the observers
     */
    public void notifyFileObservers(FileProgressEvent e) {
        int i = 0;
        while (i < fileObservers.size()) {
            WeakReference<FileProgressListener> observerRef = fileObservers.get(i);
            if (observerRef == null) {
                fileObservers.remove(observerRef);
            } else {
                FileProgressListener l = observerRef.get();
                if (l != null) {
                    l.progressNotification(e);
                }
                i++;
            }
        }
    }

    /**
     * Register a observer (listener) to be informed about progress and completion.
     * 
     * @param observer
     *            the observer you want notified
     */
    public void registerConstructObserver(ConstructProgressListener observer) {
        constructObservers.add(new WeakReference<ConstructProgressListener>(observer));
    }

    /**
     * Register a observer (listener) to be informed about progress and completion.
     * 
     * @param observer
     *            the observer you want notified
     */
    public void registerFileObserver(FileProgressListener observer) {
        fileObservers.add(new WeakReference<FileProgressListener>(observer));
    }

    /**
     * Set the autorepair
     * 
     * @param autorepair
     *            the autorepair to set
     */
    public void setAutorepair(boolean autorepair) {
        this.autorepair = autorepair;
    }

    /**
     * Set the construction notification rate - how many lines need to be constructed before getting a notification
     * 
     * @param constructionNotificationRate
     *            the construction notification rate - how many lines need to be constructed before getting a
     *            notification. Must be 1 or greater.
     */
    public void setConstructionNotificationRate(int constructionNotificationRate) {
        if (constructionNotificationRate < 1) {
            throw new IllegalArgumentException("Construction Notification Rate must be at least 1");
        }
        this.constructionNotificationRate = constructionNotificationRate;
    }

    /**
     * Set the number of lines to be written between each file notification
     * 
     * @param fileNotificationRate
     *            the number of lines to be written between each file notification. Must be 1 or greater.
     */
    public void setFileNotificationRate(int fileNotificationRate) {
        if (fileNotificationRate < 1) {
            throw new IllegalArgumentException("File Notification Rate must be at least 1");
        }
        this.fileNotificationRate = fileNotificationRate;
    }

    /**
     * Set the useLittleEndianForUnicode
     * 
     * @param useLittleEndianForUnicode
     *            the useLittleEndianForUnicode to set
     */
    public void setUseLittleEndianForUnicode(boolean useLittleEndianForUnicode) {
        this.useLittleEndianForUnicode = useLittleEndianForUnicode;
    }

    /**
     * Unregister a observer (listener) to be informed about progress and completion.
     * 
     * @param observer
     *            the observer you want notified
     */
    public void unregisterConstructObserver(ConstructProgressListener observer) {
        int i = 0;
        while (i < constructObservers.size()) {
            WeakReference<ConstructProgressListener> observerRef = constructObservers.get(i);
            if (observerRef == null || observerRef.get() == observer) {
                constructObservers.remove(observerRef);
            } else {
                i++;
            }
        }
        constructObservers.add(new WeakReference<ConstructProgressListener>(observer));
    }

    /**
     * Unregister a observer (listener) to be informed about progress and completion.
     * 
     * @param observer
     *            the observer you want notified
     */
    public void unregisterFileObserver(FileProgressListener observer) {
        int i = 0;
        while (i < fileObservers.size()) {
            WeakReference<FileProgressListener> observerRef = fileObservers.get(i);
            if (observerRef == null || observerRef.get() == observer) {
                fileObservers.remove(observerRef);
            } else {
                i++;
            }
        }
        fileObservers.add(new WeakReference<FileProgressListener>(observer));
    }

    /**
     * Write the {@link Gedcom} data as a GEDCOM 5.5 file. Automatically fills in the value for the FILE tag in the HEAD
     * structure.
     * 
     * @param file
     *            the {@link File} to write to
     * @throws IOException
     *             if there's a problem writing the data
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    public void write(File file) throws IOException, GedcomWriterException {
        // Automatically replace the contents of the filename in the header
        writeFrom.getHeader().setFileName(new StringWithCustomTags(file.getName()));

        // If the file doesn't exist yet, we have to create it, otherwise a FileNotFoundException will be thrown
        if (!file.exists() && !file.getCanonicalFile().getParentFile().exists() && !file.getCanonicalFile().getParentFile().mkdirs() && !file.createNewFile()) {
            throw new IOException("Unable to create file " + file.getName());
        }
        OutputStream o = new FileOutputStream(file);
        try {
            write(o);
            o.flush();
        } finally {
            o.close();
        }
    }

    /**
     * Write the {@link Gedcom} data in GEDCOM 5.5 format to an output stream
     * 
     * @param out
     *            the output stream we're writing to
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written; or if the data fails validation with one or more
     *             finding of severity ERROR (and validation is not suppressed - see
     *             {@link GedcomWriter#validationSuppressed})
     */
    public void write(OutputStream out) throws GedcomWriterException {
        emit();
        try {
            GedcomFileWriter gfw = new GedcomFileWriter(this, lines);
            gfw.setUseLittleEndianForUnicode(useLittleEndianForUnicode);
            gfw.write(out);
        } catch (IOException e) {
            throw new GedcomWriterException("Unable to write file", e);
        }
    }

    /**
     * Write the {@link Gedcom} data as a GEDCOM 5.5 file, with the supplied file name
     * 
     * @param filename
     *            the name of the file to write
     * @throws IOException
     *             if there's a problem writing the data
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    public void write(String filename) throws IOException, GedcomWriterException {
        File f = new File(filename);
        write(f);
    }

    @Override
    protected void emit() throws GedcomWriterException {
        if (!validationSuppressed) {
            GedcomValidator gv = new GedcomValidator(writeFrom);
            gv.setAutorepairEnabled(autorepair);
            gv.validate();
            validationFindings = gv.getFindings();
            int numErrorFindings = 0;
            for (GedcomValidationFinding f : validationFindings) {
                if (f.getSeverity() == Severity.ERROR) {
                    numErrorFindings++;
                }
            }
            if (numErrorFindings > 0) {
                throw new GedcomWriterException("Cannot write file - " + numErrorFindings
                        + " error(s) found during validation.  Review the validation findings to determine root cause.");
            }
        }
        checkVersionCompatibility();
        new HeaderEmitter(baseWriter, 0, writeFrom.getHeader()).emit();
        emitSubmissionRecord();
        emitRecords();
        emitTrailer();
        emitCustomTags(1, writeFrom.getCustomTags());
    }

    /**
     * Emit the custom tags
     * 
     * @param customTags
     *            the custom tags
     * @param level
     *            the level at which the custom tags are to be written
     */
    @Override
    void emitCustomTags(int level, List<StringTree> customTags) {
        if (customTags != null) {
            for (StringTree st : customTags) {
                StringBuilder line = new StringBuilder(Integer.toString(level));
                line.append(" ");
                if (st.getId() != null && st.getId().trim().length() > 0) {
                    line.append(st.getId()).append(" ");
                }
                line.append(st.getTag());
                if (st.getValue() != null && st.getValue().trim().length() > 0) {
                    line.append(" ").append(st.getValue());
                }
                baseWriter.lines.add(line.toString());
                emitCustomTags(level + 1, st.getChildren());
            }
        }
    }

    /**
     * Notify construct observers if more than 100 lines have been constructed since last time we notified them
     */
    void notifyConstructObserversIfNeeded() {
        if ((lines.size() - lastLineCountNotified) > constructionNotificationRate) {
            notifyConstructObservers(new ConstructProgressEvent(this, lines.size(), true));
        }
    }

    /**
     * Checks that the gedcom version specified is compatible with the data in the model. Not a perfect exhaustive
     * check.
     * 
     * @throws GedcomWriterException
     *             if data is detected that is incompatible with the selected version
     */
    private void checkVersionCompatibility() throws GedcomWriterException {

        if (writeFrom.getHeader().getGedcomVersion() == null) {
            // If there's not one specified, set up a default one that specifies
            // 5.5.1
            writeFrom.getHeader().setGedcomVersion(new GedcomVersion());
        }
        if (SupportedVersion.V5_5.equals(writeFrom.getHeader().getGedcomVersion().getVersionNumber())) {
            checkVersionCompatibility55();
        } else {
            checkVersionCompatibility551();
        }

    }

    /**
     * Check that the data is compatible with 5.5 style Gedcom files
     * 
     * @throws GedcomWriterVersionDataMismatchException
     *             if a data point is detected that is incompatible with the 5.5 standard
     */
    private void checkVersionCompatibility55() throws GedcomWriterVersionDataMismatchException {
        // Now that we know if we're working with a 5.5.1 file or not, let's
        // check some data points
        if (writeFrom.getHeader().getCopyrightData() != null && writeFrom.getHeader().getCopyrightData().size() > 1) {
            throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but has multi-line copyright data in header");
        }
        if (writeFrom.getHeader().getCharacterSet() != null && writeFrom.getHeader().getCharacterSet().getCharacterSetName() != null && "UTF-8".equals(writeFrom
                .getHeader().getCharacterSet().getCharacterSetName().getValue())) {
            throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but data is encoded using UTF-8");
        }
        if (writeFrom.getHeader().getSourceSystem() != null && writeFrom.getHeader().getSourceSystem().getCorporation() != null) {
            Corporation c = writeFrom.getHeader().getSourceSystem().getCorporation();
            if (c.getWwwUrls() != null && !c.getWwwUrls().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but source system corporation has www urls");
            }
            if (c.getFaxNumbers() != null && !c.getFaxNumbers().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but source system corporation has fax numbers");
            }
            if (c.getEmails() != null && !c.getEmails().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but source system corporation has emails");
            }
        }
        for (Individual i : writeFrom.getIndividuals().values()) {
            if (i.getWwwUrls() != null && !i.getWwwUrls().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Individual " + i.getXref() + " has www urls");
            }
            if (i.getFaxNumbers() != null && !i.getFaxNumbers().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Individual " + i.getXref() + " has fax numbers");
            }
            if (i.getEmails() != null && !i.getEmails().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Individual " + i.getXref() + " has emails");
            }
            if (i.getEvents() != null) {
                for (AbstractEvent e : i.getEvents()) {
                    if (e.getWwwUrls() != null && !e.getWwwUrls().isEmpty()) {
                        throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Individual " + i.getXref()
                                + " has www urls on an event");
                    }
                    if (e.getFaxNumbers() != null && !e.getFaxNumbers().isEmpty()) {
                        throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Individual " + i.getXref()
                                + " has fax numbers on an event");
                    }
                    if (e.getEmails() != null && !e.getEmails().isEmpty()) {
                        throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Individual " + i.getXref() + " has emails on an event");
                    }
                }
            }
            if (i.getAttributes() != null) {
                for (IndividualAttribute a : i.getAttributes()) {
                    if (IndividualAttributeType.FACT.equals(a.getType())) {
                        throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Individual " + i.getXref() + " has a FACT attribute");
                    }
                }
            }
            if (i.getFamiliesWhereChild() != null) {
                for (FamilyChild fc : i.getFamiliesWhereChild()) {
                    if (fc.getStatus() != null) {
                        throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Individual " + i.getXref()
                                + " is in a family with a status specified (a Gedcom 5.5.1 only feature)");
                    }
                }
            }
        }
        for (Submitter s : writeFrom.getSubmitters().values()) {
            if (s.getWwwUrls() != null && !s.getWwwUrls().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Submitter " + s.getXref() + " has www urls");
            }
            if (s.getFaxNumbers() != null && !s.getFaxNumbers().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Submitter " + s.getXref() + " has fax numbers");
            }
            if (s.getEmails() != null && !s.getEmails().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Submitter " + s.getXref() + " has emails");
            }
        }
        for (Repository r : writeFrom.getRepositories().values()) {
            if (r.getWwwUrls() != null && !r.getWwwUrls().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Repository " + r.getXref() + " has www urls");
            }
            if (r.getFaxNumbers() != null && !r.getFaxNumbers().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Repository " + r.getXref() + " has fax numbers");
            }
            if (r.getEmails() != null && !r.getEmails().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5, but Repository " + r.getXref() + " has emails");
            }
        }
    }

    /**
     * Check that the data is compatible with 5.5.1 style Gedcom files
     * 
     * @throws GedcomWriterVersionDataMismatchException
     *             if a data point is detected that is incompatible with the 5.5.1 standard
     * 
     */
    private void checkVersionCompatibility551() throws GedcomWriterVersionDataMismatchException {
        for (Multimedia m : writeFrom.getMultimedia().values()) {
            if (m.getBlob() != null && !m.getBlob().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("Gedcom version is 5.5.1, but multimedia item " + m.getXref()
                        + " contains BLOB data which is unsupported in 5.5.1");
            }
        }
    }

    /**
     * Emit the person-to-person associations an individual was in - see ASSOCIATION_STRUCTURE in the GEDCOM spec.
     * 
     * @param level
     *            the level at which to start recording
     * @param associations
     *            the list of associations
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitAssociationStructures(int level, List<Association> associations) throws GedcomWriterException {
        if (associations != null) {
            for (Association a : associations) {
                emitTagWithRequiredValue(level, "ASSO", a.getAssociatedEntityXref());
                emitTagWithRequiredValue(level + 1, "TYPE", a.getAssociatedEntityType());
                emitTagWithRequiredValue(level + 1, "RELA", a.getRelationship());
                new NotesEmitter(baseWriter, level + 1, a.getNotes()).emit();
                new SourceCitationEmitter(baseWriter, level + 1, a.getCitations()).emit();
                emitCustomTags(level + 1, a.getCustomTags());
            }
        }
    }

    /**
     * Emit links to all the families to which this individual was a child
     * 
     * @param level
     *            the level in the hierarchy at which we are emitting
     * @param i
     *            the individual we're dealing with
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitChildToFamilyLinks(int level, Individual i) throws GedcomWriterException {
        if (i.getFamiliesWhereChild() != null) {
            for (FamilyChild familyChild : i.getFamiliesWhereChild()) {
                if (familyChild == null) {
                    throw new GedcomWriterException("Family to which " + i + " was a child was null");
                }
                if (familyChild.getFamily() == null) {
                    throw new GedcomWriterException("Family to which " + i + " was a child had a null family reference");
                }
                emitTagWithRequiredValue(level, "FAMC", familyChild.getFamily().getXref());
                emitTagIfValueNotNull(level + 1, "PEDI", familyChild.getPedigree());
                emitTagIfValueNotNull(level + 1, "STAT", familyChild.getStatus());
                new NotesEmitter(baseWriter, level + 1, familyChild.getNotes()).emit();
                emitCustomTags(level + 1, i.getCustomTags());
            }
        }
    }

    /**
     * Emit an event detail structure - see EVENT_DETAIL in the GEDCOM spec
     * 
     * @param level
     *            the level at which we are emitting data
     * @param e
     *            the event being emitted
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitEventDetail(int level, AbstractEvent e) throws GedcomWriterException {
        emitTagIfValueNotNull(level, "TYPE", e.getSubType());
        emitTagIfValueNotNull(level, "DATE", e.getDate());
        new PlaceEmitter(baseWriter, level, e.getPlace()).emit();
        new AddressEmitter(baseWriter, level, e.getAddress()).emit();
        emitTagIfValueNotNull(level, "AGE", e.getAge());
        emitTagIfValueNotNull(level, "AGNC", e.getRespAgency());
        emitTagIfValueNotNull(level, "CAUS", e.getCause());
        emitTagIfValueNotNull(level, "RELI", e.getReligiousAffiliation());
        emitTagIfValueNotNull(level, "RESN", e.getRestrictionNotice());
        new SourceCitationEmitter(baseWriter, level, e.getCitations()).emit();
        new MultimediaLinksEmitter(baseWriter, level, e.getMultimedia()).emit();
        new NotesEmitter(baseWriter, level, e.getNotes()).emit();
        emitCustomTags(level, e.getCustomTags());
    }

    /**
     * Write out all the Families
     * 
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitFamilies() throws GedcomWriterException {
        for (Family f : writeFrom.getFamilies().values()) {
            emitTag(0, f.getXref(), "FAM");
            if (f.getEvents() != null) {
                for (FamilyEvent e : f.getEvents()) {
                    emitFamilyEventStructure(1, e);
                }
            }
            if (f.getHusband() != null) {
                emitTagWithRequiredValue(1, "HUSB", f.getHusband().getXref());
            }
            if (f.getWife() != null) {
                emitTagWithRequiredValue(1, "WIFE", f.getWife().getXref());
            }
            if (f.getChildren() != null) {
                for (Individual i : f.getChildren()) {
                    emitTagWithRequiredValue(1, "CHIL", i.getXref());
                }
            }
            emitTagIfValueNotNull(1, "NCHI", f.getNumChildren());
            if (f.getSubmitters() != null) {
                for (Submitter s : f.getSubmitters()) {
                    emitTagWithRequiredValue(1, "SUBM", s.getXref());
                }
            }
            if (f.getLdsSpouseSealings() != null) {
                for (LdsSpouseSealing s : f.getLdsSpouseSealings()) {
                    emitLdsFamilyOrdinance(1, s);
                }
            }
            emitTagIfValueNotNull(1, "RESN", f.getRestrictionNotice());
            new SourceCitationEmitter(baseWriter, 1, f.getCitations()).emit();
            new MultimediaLinksEmitter(baseWriter, 1, f.getMultimedia()).emit();
            new NotesEmitter(baseWriter, 1, f.getNotes()).emit();
            if (f.getUserReferences() != null) {
                for (UserReference u : f.getUserReferences()) {
                    emitTagWithRequiredValue(1, "REFN", u.getReferenceNum());
                    emitTagIfValueNotNull(2, "TYPE", u.getType());
                }
            }
            emitTagIfValueNotNull(1, "RIN", f.getAutomatedRecordId());
            new ChangeDateEmitter(baseWriter, 1, f.getChangeDate()).emit();
            emitCustomTags(1, f.getCustomTags());
            notifyConstructObserversIfNeeded();
            if (cancelled) {
                throw new WriterCancelledException("Construction and writing of GEDCOM cancelled");
            }
        }
    }

    /**
     * Emit a family event structure (see FAMILY_EVENT_STRUCTURE in the GEDCOM spec)
     * 
     * @param level
     *            the level we're writing at
     * @param e
     *            the event
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitFamilyEventStructure(int level, FamilyEvent e) throws GedcomWriterException {
        emitTagWithOptionalValue(level, e.getType().getTag(), e.getyNull());
        emitEventDetail(level + 1, e);
        if (e.getHusbandAge() != null) {
            emitTag(level + 1, "HUSB");
            emitTagWithRequiredValue(level + 2, "AGE", e.getHusbandAge());
        }
        if (e.getWifeAge() != null) {
            emitTag(level + 1, "WIFE");
            emitTagWithRequiredValue(level + 2, "AGE", e.getWifeAge());
        }
    }

    /**
     * Emit a collection of individual attributes
     * 
     * @param level
     *            the level to start emitting at
     * @param attributes
     *            the attributes
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitIndividualAttributes(int level, List<IndividualAttribute> attributes) throws GedcomWriterException {
        if (attributes != null) {
            for (IndividualAttribute a : attributes) {
                emitTagWithOptionalValueAndCustomSubtags(level, a.getType().getTag(), a.getDescription());
                emitEventDetail(level + 1, a);
                new AddressEmitter(baseWriter, level + 1, a.getAddress()).emit();
                emitStringsWithCustomTags(level + 1, a.getPhoneNumbers(), "PHON");
                emitStringsWithCustomTags(level + 1, a.getWwwUrls(), "WWW");
                emitStringsWithCustomTags(level + 1, a.getFaxNumbers(), "FAX");
                emitStringsWithCustomTags(level + 1, a.getEmails(), "EMAIL");
            }
        }
    }

    /**
     * Emit a collection of individual events
     * 
     * @param level
     *            the level to start emitting at
     * @param events
     *            the events
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitIndividualEvents(int level, List<IndividualEvent> events) throws GedcomWriterException {
        if (events != null) {
            for (IndividualEvent e : events) {
                emitTagWithOptionalValue(level, e.getType().getTag(), e.getyNull());
                emitEventDetail(level + 1, e);
                if (e.getType() == IndividualEventType.BIRTH || e.getType() == IndividualEventType.CHRISTENING) {
                    if (e.getFamily() != null && e.getFamily().getFamily() != null && e.getFamily().getFamily().getXref() != null) {
                        emitTagWithRequiredValue(level + 1, "FAMC", e.getFamily().getFamily().getXref());
                    }
                } else if (e.getType() == IndividualEventType.ADOPTION && e.getFamily() != null && e.getFamily().getFamily() != null && e.getFamily()
                        .getFamily().getXref() != null) {
                    emitTagWithRequiredValue(level + 1, "FAMC", e.getFamily().getFamily().getXref());
                    emitTagIfValueNotNull(level + 2, "ADOP", e.getFamily().getAdoptedBy());
                }
            }
        }
    }

    /**
     * Write out all the individuals at the root level
     * 
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitIndividuals() throws GedcomWriterException {
        for (Individual i : writeFrom.getIndividuals().values()) {
            emitTag(0, i.getXref(), "INDI");
            emitTagIfValueNotNull(1, "RESN", i.getRestrictionNotice());
            emitPersonalNames(1, i.getNames());
            emitTagIfValueNotNull(1, "SEX", i.getSex());
            emitIndividualEvents(1, i.getEvents());
            emitIndividualAttributes(1, i.getAttributes());
            emitLdsIndividualOrdinances(1, i.getLdsIndividualOrdinances());
            emitChildToFamilyLinks(1, i);
            emitSpouseInFamilyLinks(1, i);
            if (i.getSubmitters() != null) {
                for (Submitter s : i.getSubmitters()) {
                    emitTagWithRequiredValue(1, "SUBM", s.getXref());
                }
            }
            emitAssociationStructures(1, i.getAssociations());
            if (i.getAliases() != null) {
                for (StringWithCustomTags s : i.getAliases()) {
                    emitTagWithRequiredValue(1, "ALIA", s);
                }
            }
            if (i.getAncestorInterest() != null) {
                for (Submitter s : i.getAncestorInterest()) {
                    emitTagWithRequiredValue(1, "ANCI", s.getXref());
                }
            }
            if (i.getDescendantInterest() != null) {
                for (Submitter s : i.getDescendantInterest()) {
                    emitTagWithRequiredValue(1, "DESI", s.getXref());
                }
            }
            new SourceCitationEmitter(baseWriter, 1, i.getCitations()).emit();
            new MultimediaLinksEmitter(baseWriter, 1, i.getMultimedia()).emit();
            new NotesEmitter(baseWriter, 1, i.getNotes()).emit();
            emitTagIfValueNotNull(1, "RFN", i.getPermanentRecFileNumber());
            emitTagIfValueNotNull(1, "AFN", i.getAncestralFileNumber());
            if (i.getUserReferences() != null) {
                for (UserReference u : i.getUserReferences()) {
                    emitTagWithRequiredValue(1, "REFN", u.getReferenceNum());
                    emitTagIfValueNotNull(2, "TYPE", u.getType());
                }
            }
            emitTagIfValueNotNull(1, "RIN", i.getRecIdNumber());
            new ChangeDateEmitter(baseWriter, 1, i.getChangeDate()).emit();
            emitCustomTags(1, i.getCustomTags());
            notifyConstructObserversIfNeeded();
            if (cancelled) {
                throw new WriterCancelledException("Construction and writing of GEDCOM cancelled");
            }
        }
    }

    /**
     * Emit the LDS spouse sealing information
     * 
     * @param level
     *            the level we're writing at
     * @param sealings
     *            the {@link LdsSpouseSealing} structure
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitLdsFamilyOrdinance(int level, LdsSpouseSealing sealings) throws GedcomWriterException {
        emitTag(level, "SLGS");
        emitTagIfValueNotNull(level + 1, "STAT", sealings.getStatus());
        emitTagIfValueNotNull(level + 1, "DATE", sealings.getDate());
        emitTagIfValueNotNull(level + 1, "TEMP", sealings.getTemple());
        emitTagIfValueNotNull(level + 1, "PLAC", sealings.getPlace());
        new SourceCitationEmitter(baseWriter, level + 1, sealings.getCitations()).emit();
        new NotesEmitter(baseWriter, level + 1, sealings.getNotes()).emit();
        emitCustomTags(level + 1, sealings.getCustomTags());
    }

    /**
     * Emit the LDS individual ordinances
     * 
     * @param level
     *            the level in the hierarchy we are processing
     * @param ldsIndividualOrdinances
     *            the list of LDS individual ordinances to emit
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitLdsIndividualOrdinances(int level, List<LdsIndividualOrdinance> ldsIndividualOrdinances) throws GedcomWriterException {
        if (ldsIndividualOrdinances != null) {
            for (LdsIndividualOrdinance o : ldsIndividualOrdinances) {
                emitTagWithOptionalValue(level, o.getType().getTag(), o.getyNull());
                emitTagIfValueNotNull(level + 1, "STAT", o.getStatus());
                emitTagIfValueNotNull(level + 1, "DATE", o.getDate());
                emitTagIfValueNotNull(level + 1, "TEMP", o.getTemple());
                emitTagIfValueNotNull(level + 1, "PLAC", o.getPlace());
                if (o.getType() == LdsIndividualOrdinanceType.CHILD_SEALING) {
                    if (o.getFamilyWhereChild() == null) {
                        throw new GedcomWriterException("LDS Ordinance info for a child sealing had no reference to a family");
                    }
                    if (o.getFamilyWhereChild().getFamily() == null) {
                        throw new GedcomWriterException("LDS Ordinance info for a child sealing had familyChild object with a null reference to a family");
                    }
                    emitTagWithRequiredValue(level + 1, "FAMC", o.getFamilyWhereChild().getFamily().getXref());
                }
                new SourceCitationEmitter(baseWriter, level + 1, o.getCitations()).emit();
                new NotesEmitter(baseWriter, level + 1, o.getNotes()).emit();
                emitCustomTags(level + 1, o.getCustomTags());
            }
        }
    }

    /**
     * Write out all the embedded multimedia objects in GEDCOM 5.5 format
     * 
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitMultimedia55() throws GedcomWriterException {
        for (Multimedia m : writeFrom.getMultimedia().values()) {
            emitTag(0, m.getXref(), "OBJE");
            emitTagWithRequiredValue(1, "FORM", m.getEmbeddedMediaFormat());
            emitTagIfValueNotNull(1, "TITL", m.getEmbeddedTitle());
            new NotesEmitter(baseWriter, 1, m.getNotes()).emit();
            emitTag(1, "BLOB");
            for (String b : m.getBlob()) {
                emitTagWithRequiredValue(2, "CONT", b);
            }
            if (m.getContinuedObject() != null && m.getContinuedObject().getXref() != null) {
                emitTagWithRequiredValue(1, "OBJE", m.getContinuedObject().getXref());
            }
            if (m.getUserReferences() != null) {
                for (UserReference u : m.getUserReferences()) {
                    emitTagWithRequiredValue(1, "REFN", u.getReferenceNum());
                    emitTagIfValueNotNull(2, "TYPE", u.getType());
                }
            }
            emitTagIfValueNotNull(1, "RIN", m.getRecIdNumber());
            new ChangeDateEmitter(baseWriter, 1, m.getChangeDate()).emit();
            if (m.getFileReferences() != null && !m.getFileReferences().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("GEDCOM version is 5.5, but found file references in multimedia object " + m.getXref()
                        + " which are not allowed until GEDCOM 5.5.1");
            }
            emitCustomTags(1, m.getCustomTags());
            notifyConstructObserversIfNeeded();
            if (cancelled) {
                throw new WriterCancelledException("Construction and writing of GEDCOM cancelled");
            }
        }
    }

    /**
     * Write out all the embedded multimedia objects in GEDCOM 5.5.1 format
     * 
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitMultimedia551() throws GedcomWriterException {
        for (Multimedia m : writeFrom.getMultimedia().values()) {
            emitTag(0, m.getXref(), "OBJE");
            if (m.getFileReferences() != null) {
                for (FileReference fr : m.getFileReferences()) {
                    emitTagWithRequiredValue(1, "FILE", fr.getReferenceToFile());
                    emitTagWithRequiredValue(2, "FORM", fr.getFormat());
                    emitTagIfValueNotNull(3, "TYPE", fr.getMediaType());
                    emitTagIfValueNotNull(2, "TITL", fr.getTitle());
                }
            }
            if (m.getUserReferences() != null) {
                for (UserReference u : m.getUserReferences()) {
                    emitTagWithRequiredValue(1, "REFN", u.getReferenceNum());
                    emitTagIfValueNotNull(2, "TYPE", u.getType());
                }
            }
            emitTagIfValueNotNull(1, "RIN", m.getRecIdNumber());
            new NotesEmitter(baseWriter, 1, m.getNotes()).emit();
            new ChangeDateEmitter(baseWriter, 1, m.getChangeDate()).emit();
            emitCustomTags(1, m.getCustomTags());
            if (m.getBlob() != null && !m.getBlob().isEmpty()) {
                throw new GedcomWriterVersionDataMismatchException("GEDCOM version is 5.5.1, but BLOB data on multimedia item " + m.getXref()
                        + " was found.  This is only allowed in GEDCOM 5.5");
            }
            if (m.getContinuedObject() != null) {
                throw new GedcomWriterVersionDataMismatchException("GEDCOM version is 5.5.1, but BLOB continuation data on multimedia item " + m.getXref()
                        + " was found.  This is only allowed in GEDCOM 5.5");
            }
            if (m.getEmbeddedMediaFormat() != null) {
                throw new GedcomWriterVersionDataMismatchException("GEDCOM version is 5.5.1, but format on multimedia item " + m.getXref()
                        + " was found.  This is only allowed in GEDCOM 5.5");
            }
            if (m.getEmbeddedTitle() != null) {
                throw new GedcomWriterVersionDataMismatchException("GEDCOM version is 5.5.1, but title on multimedia item " + m.getXref()
                        + " was found.  This is only allowed in GEDCOM 5.5");
            }
            notifyConstructObserversIfNeeded();
            if (cancelled) {
                throw new WriterCancelledException("Construction and writing of GEDCOM cancelled");
            }
        }
    }

    /**
     * Emit a list of personal names for an individual
     * 
     * @param level
     *            the level to start emitting at
     * @param names
     *            the names
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitPersonalNames(int level, List<PersonalName> names) throws GedcomWriterException {
        if (names != null) {
            for (PersonalName n : names) {
                emitTagWithOptionalValue(level, "NAME", n.getBasic());
                emitTagIfValueNotNull(level + 1, "NPFX", n.getPrefix());
                emitTagIfValueNotNull(level + 1, "GIVN", n.getGivenName());
                emitTagIfValueNotNull(level + 1, "NICK", n.getNickname());
                emitTagIfValueNotNull(level + 1, "SPFX", n.getSurnamePrefix());
                emitTagIfValueNotNull(level + 1, "SURN", n.getSurname());
                emitTagIfValueNotNull(level + 1, "NSFX", n.getSuffix());
                if (n.getRomanized() != null) {
                    for (PersonalNameVariation pnv : n.getRomanized()) {
                        emitPersonalNameVariation(level + 1, "ROMN", pnv);
                    }
                }
                if (n.getPhonetic() != null) {
                    for (PersonalNameVariation pnv : n.getPhonetic()) {
                        emitPersonalNameVariation(level + 1, "FONE", pnv);
                    }
                }
                new SourceCitationEmitter(baseWriter, level + 1, n.getCitations()).emit();
                new NotesEmitter(baseWriter, level + 1, n.getNotes()).emit();
                emitCustomTags(level + 1, n.getCustomTags());
            }
        }
    }

    /**
     * Emit a personal name variation - either romanized or phonetic
     * 
     * @param level
     *            - the level we are writing at
     * @param variationTag
     *            the tag for the type of variation - should be "ROMN" for romanized, or "FONE" for phonetic
     * @param pnv
     *            - the personal name variation we are writing
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitPersonalNameVariation(int level, String variationTag, PersonalNameVariation pnv) throws GedcomWriterException {
        emitTagWithRequiredValue(level, variationTag, pnv.getVariation());
        emitTagIfValueNotNull(level + 1, "NPFX", pnv.getPrefix());
        emitTagIfValueNotNull(level + 1, "GIVN", pnv.getGivenName());
        emitTagIfValueNotNull(level + 1, "NICK", pnv.getNickname());
        emitTagIfValueNotNull(level + 1, "SPFX", pnv.getSurnamePrefix());
        emitTagIfValueNotNull(level + 1, "SURN", pnv.getSurname());
        emitTagIfValueNotNull(level + 1, "NSFX", pnv.getSuffix());
        new SourceCitationEmitter(baseWriter, level + 1, pnv.getCitations()).emit();
        new NotesEmitter(baseWriter, level + 1, pnv.getNotes()).emit();
        emitCustomTags(level + 1, pnv.getCustomTags());
    }

    /**
     * Write the records (see the RECORD structure in the GEDCOM standard)
     * 
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitRecords() throws GedcomWriterException {
        notifyConstructObserversIfNeeded();
        emitIndividuals();
        emitFamilies();
        if (g55()) {
            emitMultimedia55();
        } else {
            emitMultimedia551();
        }
        new NotesEmitter(baseWriter, 0, new ArrayList<Note>(writeFrom.getNotes().values())).emit();
        emitRepositories();
        emitSources();
        new SubmittersEmitter(this, 0, writeFrom.getSubmitters().values()).emit();
    }

    /**
     * Write out all the repositories (see REPOSITORY_RECORD in the Gedcom spec)
     * 
     * @throws GedcomWriterException
     *             if the data being written is malformed
     */
    private void emitRepositories() throws GedcomWriterException {
        for (Repository r : writeFrom.getRepositories().values()) {
            emitTag(0, r.getXref(), "REPO");
            emitTagIfValueNotNull(1, "NAME", r.getName());
            new AddressEmitter(baseWriter, 1, r.getAddress()).emit();
            new NotesEmitter(baseWriter, 1, r.getNotes()).emit();
            if (r.getUserReferences() != null) {
                for (UserReference u : r.getUserReferences()) {
                    emitTagWithRequiredValue(1, "REFN", u.getReferenceNum());
                    emitTagIfValueNotNull(2, "TYPE", u.getType());
                }
            }
            emitTagIfValueNotNull(1, "RIN", r.getRecIdNumber());
            emitStringsWithCustomTags(1, r.getPhoneNumbers(), "PHON");
            emitStringsWithCustomTags(1, r.getWwwUrls(), "WWW");
            emitStringsWithCustomTags(1, r.getFaxNumbers(), "FAX");
            emitStringsWithCustomTags(1, r.getEmails(), "EMAIL");
            new ChangeDateEmitter(baseWriter, 1, r.getChangeDate()).emit();
            emitCustomTags(1, r.getCustomTags());
            notifyConstructObserversIfNeeded();
            if (cancelled) {
                throw new WriterCancelledException("Construction and writing of GEDCOM cancelled");
            }
        }
    }

    /**
     * Write out a repository citation (see SOURCE_REPOSITORY_CITATION in the gedcom spec)
     * 
     * @param level
     *            the level we're writing at
     * @param repositoryCitation
     *            the repository citation to write out
     * @throws GedcomWriterException
     *             if the repository citation passed in has a null repository reference
     */
    private void emitRepositoryCitation(int level, RepositoryCitation repositoryCitation) throws GedcomWriterException {
        if (repositoryCitation != null) {
            if (repositoryCitation.getRepositoryXref() == null) {
                throw new GedcomWriterException("Repository Citation has null repository reference");
            }
            emitTagWithRequiredValue(level, "REPO", repositoryCitation.getRepositoryXref());
            new NotesEmitter(baseWriter, level + 1, repositoryCitation.getNotes()).emit();
            if (repositoryCitation.getCallNumbers() != null) {
                for (SourceCallNumber scn : repositoryCitation.getCallNumbers()) {
                    emitTagWithRequiredValue(level + 1, "CALN", scn.getCallNumber());
                    emitTagIfValueNotNull(level + 2, "MEDI", scn.getMediaType());
                }
            }
            emitCustomTags(level + 1, repositoryCitation.getCustomTags());
        }

    }

    /**
     * Write out all the sources (see SOURCE_RECORD in the Gedcom spec)
     * 
     * @throws GedcomWriterException
     *             if the data being written is malformed
     */
    private void emitSources() throws GedcomWriterException {
        for (Source s : writeFrom.getSources().values()) {
            emitTag(0, s.getXref(), "SOUR");
            SourceData d = s.getData();
            if (d != null) {
                emitTag(1, "DATA");
                for (EventRecorded e : d.getEventsRecorded()) {
                    emitTagWithOptionalValue(2, "EVEN", e.getEventType());
                    emitTagIfValueNotNull(3, "DATE", e.getDatePeriod());
                    emitTagIfValueNotNull(3, "PLAC", e.getJurisdiction());
                }
                emitTagIfValueNotNull(2, "AGNC", d.getRespAgency());
                new NotesEmitter(baseWriter, 2, d.getNotes()).emit();
            }
            emitLinesOfText(1, "AUTH", s.getOriginatorsAuthors());
            emitLinesOfText(1, "TITL", s.getTitle());
            emitTagIfValueNotNull(1, "ABBR", s.getSourceFiledBy());
            emitLinesOfText(1, "PUBL", s.getPublicationFacts());
            emitLinesOfText(1, "TEXT", s.getSourceText());
            emitRepositoryCitation(1, s.getRepositoryCitation());
            new MultimediaLinksEmitter(baseWriter, 1, s.getMultimedia()).emit();
            new NotesEmitter(baseWriter, 1, s.getNotes()).emit();
            if (s.getUserReferences() != null) {
                for (UserReference u : s.getUserReferences()) {
                    emitTagWithRequiredValue(1, "REFN", u.getReferenceNum());
                    emitTagIfValueNotNull(2, "TYPE", u.getType());
                }
            }
            emitTagIfValueNotNull(1, "RIN", s.getRecIdNumber());
            new ChangeDateEmitter(baseWriter, 1, s.getChangeDate()).emit();
            emitCustomTags(1, s.getCustomTags());
            notifyConstructObserversIfNeeded();
            if (cancelled) {
                throw new WriterCancelledException("Construction and writing of GEDCOM cancelled");
            }
        }
    }

    /**
     * Emit links to all the families to which the supplied individual was a spouse
     * 
     * @param level
     *            the level in the hierarchy at which we are emitting
     * @param i
     *            the individual we are dealing with
     * @throws GedcomWriterException
     *             if data is malformed and cannot be written
     */
    private void emitSpouseInFamilyLinks(int level, Individual i) throws GedcomWriterException {
        if (i.getFamiliesWhereSpouse() != null) {
            for (FamilySpouse familySpouse : i.getFamiliesWhereSpouse()) {
                if (familySpouse == null) {
                    throw new GedcomWriterException("Family in which " + i + " was a spouse was null");
                }
                if (familySpouse.getFamily() == null) {
                    throw new GedcomWriterException("Family in which " + i + " was a spouse had a null family reference");
                }
                emitTagWithRequiredValue(level, "FAMS", familySpouse.getFamily().getXref());
                new NotesEmitter(baseWriter, level + 1, familySpouse.getNotes()).emit();
                emitCustomTags(level + 1, familySpouse.getCustomTags());
            }
        }
    }

    /**
     * Write the SUBMISSION_RECORD
     * 
     * @throws GedcomWriterException
     *             if the data is malformed and cannot be written
     */
    private void emitSubmissionRecord() throws GedcomWriterException {
        Submission s = writeFrom.getSubmission();
        if (s == null) {
            return;
        }
        emitTag(0, s.getXref(), "SUBN");
        if (s.getSubmitter() != null) {
            emitTagWithOptionalValue(1, "SUBM", s.getSubmitter().getXref());
        }
        emitTagIfValueNotNull(1, "FAMF", s.getNameOfFamilyFile());
        emitTagIfValueNotNull(1, "TEMP", s.getTempleCode());
        emitTagIfValueNotNull(1, "ANCE", s.getAncestorsCount());
        emitTagIfValueNotNull(1, "DESC", s.getDescendantsCount());
        emitTagIfValueNotNull(1, "ORDI", s.getOrdinanceProcessFlag());
        emitTagIfValueNotNull(1, "RIN", s.getRecIdNumber());
        emitCustomTags(1, s.getCustomTags());
    }

    /**
     * Write out the trailer record
     */
    private void emitTrailer() {
        lines.add("0 TRLR");
        notifyConstructObservers(new ConstructProgressEvent(this, lines.size(), true));
    }

    /**
     * Notify all listeners about the line being
     * 
     * @param e
     *            the change event to tell the observers
     */
    private void notifyConstructObservers(ConstructProgressEvent e) {
        int i = 0;
        lastLineCountNotified = e.getLinesProcessed();
        while (i < constructObservers.size()) {
            WeakReference<ConstructProgressListener> observerRef = constructObservers.get(i);
            if (observerRef == null) {
                constructObservers.remove(observerRef);
            } else {
                ConstructProgressListener l = observerRef.get();
                if (l != null) {
                    l.progressNotification(e);
                }
                i++;
            }
        }
    }
}
