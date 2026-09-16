package de.chojo.lyna.configuration;

import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.configuration.elements.Auth;
import de.chojo.lyna.configuration.elements.BaseSettings;
import de.chojo.lyna.configuration.elements.Database;
import de.chojo.lyna.configuration.elements.Demo;
import de.chojo.lyna.configuration.elements.Downloads;
import de.chojo.lyna.configuration.elements.Discord;
import de.chojo.lyna.configuration.elements.Kofi;
import de.chojo.lyna.configuration.elements.License;
import de.chojo.lyna.configuration.elements.Links;
import de.chojo.lyna.configuration.elements.Mailing;
import de.chojo.lyna.configuration.elements.Nexus;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class ConfigFile {
    private BaseSettings baseSettings = new BaseSettings();
    private Database database = new Database();
    private Links links = new Links();
    private License license = new License();
    private Nexus nexus = new Nexus();
    private Api api = new Api();
    private Mailing mailing = new Mailing();
    private Kofi kofi = new Kofi();
    private Auth auth = new Auth();
    private Discord discord = new Discord();
    private Demo demo = new Demo();
    private Downloads downloads = new Downloads();

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
    public Nexus nexus() {
        return nexus;
    }

    public Api api() {
        return api;
    }

    public Mailing mailing() {
        return mailing;
    }
    public Kofi kofi() {
        return kofi;
    }

    public Auth auth() {
        return auth;
    }

    public Discord discord() {
        return discord;
    }

    public Downloads downloads() {
        return downloads;
    }

    public Demo demo() {
        return demo;
    }
}
