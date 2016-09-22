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
package org.gedcom4j.model;

import java.util.ArrayList;
import java.util.List;

import org.gedcom4j.Options;

/**
 * A custom fact that represents a custom tag. Has first-order support for dates, places, notes, and source-citations. All other
 * substructures are treated as further CustomFacts.
 * 
 * @author frizbog
 */
public class CustomFact extends AbstractNotesElement implements HasCitations, HasXref {

    /**
     * Serial Version UID
     */
    private static final long serialVersionUID = 8140277678428809773L;

    /**
     * Get the serialVersionUID
     * 
     * @return the serialVersionUID
     */
    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    /**
     * The citations for this object
     */
    private List<AbstractCitation> citations = getCitations(Options.isCollectionInitializationEnabled());

    /**
     * The date of this event
     */
    private StringWithCustomFacts date;

    /**
     * A description of this event
     */
    private StringWithCustomFacts description;

    /**
     * The place where this event occurred
     */
    private Place place;

    /**
     * The custom tag string that identified this fact
     */
    private final String tag;

    /**
     * The xref for this submitter
     */
    private String xref;

    /**
     * Copy constructor
     * 
     * @param other
     *            the custom fact to copy
     */
    public CustomFact(CustomFact other) {
        super(other);
        tag = other.tag;
        if (other.date != null) {
            date = new StringWithCustomFacts(other.date);
        }
        if (other.description != null) {
            description = new StringWithCustomFacts(other.description);
        }
        if (other.place != null) {
            place = new Place(other.place);
        }
        if (other.citations != null) {
            for (AbstractCitation c : other.citations) {
                if (c instanceof CitationWithoutSource) {
                    getCitations(true).add(new CitationWithoutSource((CitationWithoutSource) c));
                } else {
                    getCitations(true).add(new CitationWithSource((CitationWithSource) c));
                }
            }
        }
    }

    /**
     * Constructor
     * 
     * @param tag
     *            the tag that represents this structure in the GEDCOM file
     */
    public CustomFact(String tag) {
        this.tag = tag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof CustomFact)) {
            return false;
        }
        CustomFact other = (CustomFact) obj;
        if (citations == null) {
            if (other.citations != null) {
                return false;
            }
        } else if (!citations.equals(other.citations)) {
            return false;
        }
        if (date == null) {
            if (other.date != null) {
                return false;
            }
        } else if (!date.equals(other.date)) {
            return false;
        }
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (place == null) {
            if (other.place != null) {
                return false;
            }
        } else if (!place.equals(other.place)) {
            return false;
        }
        if (tag == null) {
            if (other.tag != null) {
                return false;
            }
        } else if (!tag.equals(other.tag)) {
            return false;
        }
        return true;
    }

    /**
     * Gets the citations.
     *
     * @return the citations
     */
    @Override
    public List<AbstractCitation> getCitations() {
        return citations;
    }

    /**
     * Get the citations
     * 
     * @param initializeIfNeeded
     *            initialize the collection if needed?
     * 
     * @return the citations
     */
    @Override
    public List<AbstractCitation> getCitations(boolean initializeIfNeeded) {
        if (initializeIfNeeded && citations == null) {
            citations = new ArrayList<>(0);
        }
        return citations;
    }

    /**
     * Gets the date.
     *
     * @return the date
     */
    public StringWithCustomFacts getDate() {
        return date;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public StringWithCustomFacts getDescription() {
        return description;
    }

    /**
     * Gets the place.
     *
     * @return the place
     */
    public Place getPlace() {
        return place;
    }

    /**
     * Get the tag
     * 
     * @return the tag
     */
    public String getTag() {
        return tag;
    }

    /**
     * Gets the xref.
     *
     * @return the xref
     */
    @Override
    public String getXref() {
        return xref;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((citations == null) ? 0 : citations.hashCode());
        result = prime * result + ((date == null) ? 0 : date.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((place == null) ? 0 : place.hashCode());
        result = prime * result + ((tag == null) ? 0 : tag.hashCode());
        return result;
    }

    /**
     * Set the date from a string
     * 
     * @param dateString
     *            the date strinf
     */
    public void setDate(String dateString) {
        date = new StringWithCustomFacts(dateString);

    }

    /**
     * Set the date
     * 
     * @param date
     *            the date to set
     */
    public void setDate(StringWithCustomFacts date) {
        this.date = date;
    }

    /**
     * Set the description from a string, wrapping it on-the-fly in a {@link StringWithCustomFacts} structure
     * 
     * @param string
     *            the string value
     */
    public void setDescription(String string) {
        description = new StringWithCustomFacts(string);
    }

    /**
     * Set the description
     * 
     * @param description
     *            the description to set
     */
    public void setDescription(StringWithCustomFacts description) {
        this.description = description;
    }

    /**
     * Set the place
     * 
     * @param place
     *            the place to set
     */
    public void setPlace(Place place) {
        this.place = place;
    }

    /**
     * Sets the xref.
     *
     * @param xref
     *            the new xref
     */
    public void setXref(String xref) {
        this.xref = xref;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(50);
        builder.append("CustomFact [");
        if (citations != null) {
            builder.append("citations=");
            builder.append(citations);
            builder.append(", ");
        }
        if (date != null) {
            builder.append("date=");
            builder.append(date);
            builder.append(", ");
        }
        if (description != null) {
            builder.append("description=");
            builder.append(description);
            builder.append(", ");
        }
        if (place != null) {
            builder.append("place=");
            builder.append(place);
            builder.append(", ");
        }
        if (tag != null) {
            builder.append("tag=");
            builder.append(tag);
            builder.append(", ");
        }
        if (getNotes() != null) {
            builder.append("getNotes()=");
            builder.append(getNotes());
            builder.append(", ");
        }
        if (getCustomFacts() != null) {
            builder.append("getCustomFacts()=");
            builder.append(getCustomFacts());
        }
        builder.append("]");
        return builder.toString();
    }

}
