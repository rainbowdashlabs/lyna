package de.chojo.lyna.configuration;

import de.chojo.lyna.configuration.elements.BaseSettings;
import de.chojo.lyna.configuration.elements.Database;
import de.chojo.lyna.configuration.elements.License;
import de.chojo.lyna.configuration.elements.Links;
import de.chojo.lyna.data.dao.Licenses;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class ConfigFile {
    private BaseSettings baseSettings = new BaseSettings();
    private Database database = new Database();
    private Links links = new Links();
    private License license = new License();

    public BaseSettings baseSettings() {
        return baseSettings;
    }

    public Database database() {
        return database;
    }

    public Links links() {
        return links;
    }

    public License license() {
        return license;
    }
}
