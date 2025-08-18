package me.mrepiko.sopra;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DatabaseCredentials {

    private String id;
    private String host;
    private int port = 3306;
    @JsonAlias({"database_name"})
    private String databaseName;
    private String user;
    private String password;

}
