package com.crowdin.cli;

import com.crowdin.cli.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Created by ihor on 12/1/16.
 */
public class BaseCli {

    public static final String[] DEFAULT_CONFIGS = {"crowdin.yml", "crowdin.yaml"};

    public static final String[] DEFAULT_IDENTITY_FILES = {
        System.getProperty("user.home") + Utils.PATH_SEPARATOR + ".crowdin.yml",
        System.getProperty("user.home") + Utils.PATH_SEPARATOR + ".crowdin.yaml"
    };

    public static final Map<String, String> PLACEHOLDER_MAPPING_FOR_SERVER = new HashMap<String, String>() {{
       put("name", "language");
    }};

    public static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("messages/messages");
}
