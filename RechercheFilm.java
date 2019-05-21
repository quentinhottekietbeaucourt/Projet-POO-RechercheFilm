import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Le but de cette classe est de transformer une pseudo-requête en requete SQL puis de convertir un ResultSet en JSON.
 * @author Quentin Hottekiet - - Beaucourt - Etudiant en 3A - Classe 34 - ESIEA - Campus de Paris
 * @author Sylvain Gobalasingham - Etudiant en 3A - Classe 34 - ESIEA - Campus de Paris
 */
public class RechercheFilm {


    private String trigger_error_message = "";
    private boolean trigger_error = false;
    private Access_BDD_SQLite access_BDD_SQLite;


    /**
     * Cette classe permet la connection et la fermeture de la Base de Donnees SQLite.
     */
    public class Access_BDD_SQLite {
        
        private Connection connection = null; //Objet de connexion a la BDD SQLite

        /**
         * Constructeur de la classe
         *  @param path_BDD_SQLite Chemin de la Base de Donnees SQLite en local
         */
        public Access_BDD_SQLite(String path_BDD_SQLite) {

            String large_path = "jdbc:sqlite:" + path_BDD_SQLite;
            try 
            {   
                this.connection = DriverManager.getConnection(large_path); 
            }
            catch(Exception err) 
            {
                System.out.println("{\"trigger_error\":\"Impossible d'accéder à la BDD Sqlite\"}");
            }
        }

        /**
         * Fermeture de la Base de Donnees SQLite
         */
        public void Fermeture_BDD_SQLite() {
            
            try 
            { 
                this.connection.close();
            } 
            catch (SQLException e) 
            {   
                System.out.println("{\"trigger_error\":\"La fermeture de la base de donnees SQLite n'a pas aboutie.\"}");
            }
        }

        private ArrayList<ArrayList<String>> ResultSet_tab(String sql){

            ArrayList<ArrayList<String>> tab = new ArrayList<>();
            ResultSet resulset = null;
            PreparedStatement preparedstatement = null;

            try
            {
                preparedstatement = this.connection.prepareStatement(sql);
                resulset = preparedstatement.executeQuery();
                if (resulset.next())
                {
                    tab = TransformerResultSet(resulset);
                }
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }

            return tab;
        }
    }

    /**
     * Constructeur, ouvre la Base de Donnees.
     * @param BDD_SQLite_name Chemin et nom du fichier Base de Donnees.
     */
    public RechercheFilm(String BDD_SQLite_name) {

        this.access_BDD_SQLite = new Access_BDD_SQLite(BDD_SQLite_name);
    }

    /**
     * Cette fonction permet de transformer la pseudo-requete en requete SQL
     * @param pseudorequeteSQL Pseudo-requete
     * @return String Requete SQL creee
     */
    public String TransformerPseudoSQL(String pseudorequeteSQL) {

        pseudorequeteSQL += ",FINISHED";

        StringBuilder sql = new StringBuilder();
        StringBuilder value= new StringBuilder();

        String select = "SELECT pers_table.nom as nom_pers_table, p_table.nom as nom_pays, pers_table.prenom, f_table.titre as titre_f_table, f_table.id_film as id_film_f_table, g_table.role, f_table.duree, f_table.annee, (select group_concat(at_table.titre, '#') from autres_titres at_table where at_table.id_film=f_table.id_film) as autres_titres_table";
        String from = "\nFROM generique g_table NATURAL JOIN films f_table NATURAL JOIN personnes pers_table LEFT JOIN pays p_table ON p_table.code = f_table.pays";
        sql.append(select).append(from);
        String order_by = "\nORDER BY f_table.annee DESC, f_table.titre";
        String champ = "";

        boolean premier_where = false;
        boolean unique_titre = false;
        boolean unique_pays = false;
        boolean unique_en = false;
        boolean mot_clef_en_cours = true;
        boolean OR_entre_mots_clef = false;

        ArrayList<ArrayList<String>> matrice = new ArrayList<>();
        ArrayList<String> valeurs = new ArrayList<>();

        String[] liste_parametres = pseudorequeteSQL.split(" |((?<=,)|(?=,))");

        loop:
        for (int i = 0; i<liste_parametres.length; i++)
        { // Parcours de la pseudo-requete

            String mot = liste_parametres[i];

            if (mot_clef_en_cours)
            { // Si nous lisons un mot-clef

                mot = mot.toUpperCase();

                if (mot.equals("AVEC") || mot.equals("DE") || mot.equals("TITRE") || mot.equals("AVANT") || mot.equals("PAYS") || mot.equals("APRES") || mot.equals("EN"))
                { // Si le mot-clef fait partie des mots-clefs valides

                    // S'il s'agit d'un champ qui ne peut pas prendre de ET et que le champ a deja ete saisi
                    if ((mot.equals("EN") && unique_en) || (mot.equals("TITRE") && unique_titre) || (mot.equals("PAYS") && unique_pays))
                    {
                        this.trigger_error_message = mot + " prend qu'une unique valeur.";
                        this.trigger_error = true;
                        break;
                    }
                    else if (liste_parametres[i+1].equals(",") || liste_parametres[i+1].equals("OU"))
                    { // Pas de valeur pour le mot clef
                        this.trigger_error_message = "Des valeurs sont requises pour " + mot + ".";
                        this.trigger_error = true;
                        break;
                    }
                    else if (!champ.equals(mot))
                    { // Si tout va bien pour le mot clef
                        mot_clef_en_cours = false;
                        champ = mot;
                    }
                }
                else
                { // Si le mot n'est pas un mot-clef valide
                    if (mot.equals("FINISHED")  || (i == 0 && mot.equals(",") && liste_parametres[i+1].equals("END"))){
                        break; // Si on lit le mot-clef de la fin
                    }
                    else if (champ.matches("TITRE|PAYS|EN") && !champ.matches("DE|AVEC"))
                    {
                        this.trigger_error_message = champ + " prend qu'une unique valeur.";
                        this.trigger_error = true;
                        break;
                    }
                    else
                    { // Si on lit une autre valeur apres un "DE" ou un "AVEC"
                        i--;
                        mot_clef_en_cours=false;
                    }
                }
            }
            else { // Si on lit la valeur d'un champ

                if (mot.toUpperCase().equals("OU"))
                { // Si le mot actuel lu est un "OU"

                    if (liste_parametres[i+1] == null || liste_parametres[i+1].equals(","))
                    { // S'il n'y a pas de valeur qui suit
                        this.trigger_error_message = "Valeur attendue apres un 'OU'.";
                        this.trigger_error = true;
                        break;
                    }
                    else if (value.length() == 0)
                    { // S'il n'y a pas de valeur avant un 'OU'
                        this.trigger_error_message = "Valeur requise pour un 'OU'.";
                        this.trigger_error = true;
                        break;
                    }
                    else if(liste_parametres[i+1] != null && !champ.equals(liste_parametres[i+1]) && (liste_parametres[i+1].equals("AVEC") || liste_parametres[i+1].equals("DE") || liste_parametres[i+1].equals("TITRE") || liste_parametres[i+1].equals("AVANT") || liste_parametres[i+1].equals("PAYS") || liste_parametres[i+1].equals("APRES") || liste_parametres[i+1].equals("EN")))
                    { // Si un mot-clef est lu apres un 'OU', on concatene le SQL avec les valeurs du mot-clef precedent

                        valeurs.add(value.toString().trim());
                        value = new StringBuilder();

                        if(champ.equals("APRES"))
                        {
                            ArrayList<Integer> valeurs_2 = new ArrayList<>();

                            for (String val : valeurs) {
                                try
                                {
                                    valeurs_2.add(Integer.valueOf(val));
                                }
                                catch (NumberFormatException err)
                                {
                                    this.trigger_error_message = "Les valeurs chainees ne sont pas acceptees pour 'APRES'";
                                    this.trigger_error = true;
                                    break;
                                }
                            }

                            if (!premier_where)
                            {
                                sql.append("\nWHERE ((");
                                premier_where = true;
                            }
                            else if (OR_entre_mots_clef)
                            {
                                sql.append("\nOR (");
                            }
                            else
                            {
                                sql.append("\nAND ((");
                            }

                            if (valeurs_2.size() > 0) {
                                sql.append(" f_table.annee > ").append(Collections.min(valeurs_2)).append(")");
                            }
                        }

                        else if(champ.equals("AVANT"))
                        {
                            ArrayList<Integer> valeurs_2 = new ArrayList<>();

                            for (String val : valeurs) {
                                try
                                {
                                    valeurs_2.add(Integer.valueOf(val));
                                }
                                catch (NumberFormatException err)
                                {
                                    this.trigger_error_message = "Les valeurs chainees ne sont pas acceptees pour 'AVANT'";
                                    this.trigger_error = true;
                                    break;
                                }
                            }

                            if (!premier_where)
                            {
                                sql.append("\nWHERE ((");
                                premier_where = true;
                            }
                            else if (OR_entre_mots_clef)
                            {
                                sql.append("\nOR (");
                            }
                            else
                            {
                                sql.append("\nAND ((");
                            }

                            if (valeurs_2.size() > 0){
                                sql.append(" f_table.annee < ").append(Collections.max(valeurs_2)).append(")");
                            }
                        }

                        else if(champ.equals("AVEC"))
                        {
                            ArrayList<String> valeurs_2 = new ArrayList<>();
                            for (String val : valeurs)
                            {
                                if (val.matches(".*\\d.*"))
                                { //si la valeur contient un nombre
                                    this.trigger_error_message = "Les valeurs numeriques ne sont pas acceptees pour 'AVEC'.";
                                    this.trigger_error = true;
                                    break;
                                }
                                else
                                {
                                    valeurs_2.add(val);
                                }
                            }

                            matrice.add(valeurs_2);

                            for (ArrayList<String> val : matrice)
                            {
                                if (!premier_where)
                                {
                                    sql.append("\nWHERE ((");
                                    premier_where = true;
                                }
                                else if (OR_entre_mots_clef)
                                {
                                    sql.append("\nOR (");
                                }
                                else 
                                {
                                    sql.append("\nAND ((");
                                }

                                for (int h = 0; h < val.size(); h++) {
                                    if (h > 0)
                                    {
                                        sql.append("\nOR");
                                    }
                                    sql.append(" f_table.id_film IN (SELECT id_film FROM personnes NATURAL JOIN generique").append(" WHERE (prenom_sans_accent || ' ' || nom_sans_accent LIKE \"%").append(val.get(h).replace(' ', '%')).append("%\" OR nom_sans_accent || ' ' || prenom_sans_accent LIKE \"%").append(val.get(h).replace(' ', '%')).append("%\" OR nom_sans_accent LIKE \"%").append(val.get(h)).append("%\")").append(" AND role = 'A')");
                                }

                                sql.append(")");
                            }

                            matrice.clear();
                        }

                        else if(champ.equals("DE"))
                        {
                            ArrayList<String> valeurs_2 = new ArrayList<>();

                            for (String val : valeurs)
                            {
                                if (val.matches(".*\\d.*"))
                                { //si la valeur contient un nombre
                                    this.trigger_error_message = "Les valeurs numeriques ne sont pas acceptees pour 'DE'";
                                    this.trigger_error = true;
                                    break;
                                }
                                else{
                                    valeurs_2.add(val);
                                }
                            }

                            matrice.add(valeurs_2);

                            for (ArrayList<String> val : matrice)
                            {
                                if (!premier_where)
                                {
                                    sql.append("\nWHERE ((");
                                    premier_where = true;
                                }
                                else if (OR_entre_mots_clef)
                                {
                                    sql.append("\nOR (");
                                }
                                else {
                                    sql.append("\nAND ((");
                                }

                                for (int h = 0; h < val.size(); h++)
                                {
                                    if (h > 0) sql.append("\nOR");

                                    sql.append(" f_table.id_film IN (SELECT id_film FROM personnes NATURAL JOIN generique").append(" WHERE (prenom_sans_accent || ' ' || nom_sans_accent LIKE \"%").append(val.get(h).replace(' ', '%')).append("%\" OR nom_sans_accent || ' ' || prenom_sans_accent LIKE \"%").append(val.get(h).replace(' ', '%')).append("%\" OR nom_sans_accent LIKE \"%").append(val.get(h).replace(' ', '%')).append("%\")").append(" AND role = 'R')");
                                }

                                sql.append(")");
                            }

                            matrice.clear();
                        }

                        else if(champ.equals("EN"))
                        {
                            ArrayList<Integer> valeurs_2 = new ArrayList<>();

                            for (String val : valeurs)
                            {
                                try
                                {
                                    valeurs_2.add(Integer.valueOf(val));
                                }
                                catch (NumberFormatException err)
                                {
                                    this.trigger_error_message = "Les valeurs chainees ne sont pas acceptees pour 'EN'";
                                    this.trigger_error = true;
                                    break;
                                }
                            }

                            if (!premier_where)
                            {
                                sql.append("\nWHERE ((");
                                premier_where = true;
                            }
                            else if (OR_entre_mots_clef)
                            {
                                sql.append("\nOR (");
                            }
                            else{
                                sql.append("\nAND ((");
                            }

                            for (int j = 0; j < valeurs_2.size(); j++)
                            {
                                if (j > 0)
                                {
                                    sql.append("\nOR");
                                }

                                sql.append(" f_table.annee = ").append(valeurs_2.get(j));
                            }

                            unique_en = true;
                            sql.append(")");
                        }

                        else if(champ.equals("PAYS"))
                        {
                            if (valeurs.get(0).matches(".*\\d.*"))
                            { //si le valeur contient un nombre
                                this.trigger_error_message = "Une valeur numerique a ete saisie pour le mot-clef PAYS";
                                this.trigger_error = true;
                                break loop;
                            }
                            else
                            {
                                if (!premier_where)
                                {
                                    sql.append("\nWHERE ((");
                                    premier_where = true;
                                }
                                else if (OR_entre_mots_clef)
                                {
                                    sql.append("\nOR (");
                                }
                                else{
                                    sql.append("\nAND ((");
                                }

                                for (int j = 0; j < valeurs.size(); j++)
                                {
                                    if (j > 0)
                                    {
                                        sql.append("\nOR");
                                    }

                                    sql.append(" p_table.code LIKE '%").append(valeurs.get(j)).append("%' OR py.nom LIKE '%").append(valeurs.get(j)).append("%'");
                                }

                                sql.append(")");
                            }

                            unique_pays = true;
                        }

                        else if(champ.equals("TITRE"))
                        {
                            if (!premier_where) {
                                sql.append("\nWHERE ((");
                                premier_where = true;
                            } else if (OR_entre_mots_clef) {
                                sql.append("\nOR (");
                            } else {
                                sql.append("\nAND ((");
                            }

                            for (int j = 0; j < valeurs.size(); j++) {
                                if (j > 0) {
                                    sql.append(" OR");
                                }

                                sql.append(" f_table.id_film IN (SELECT rech_titre_table.id_film FROM recherche_titre rech_titre_table WHERE rech_titre_table.titre LIKE replace(\"%").append(valeurs.get(j)).append("%\", ' ', '%') || '%')");
                            }

                            unique_titre = true;
                            sql.append(")");
                        }

                        valeurs.clear();
                        mot_clef_en_cours = true;
                        OR_entre_mots_clef = true;
                    }
                    else if (!(mot.equals("AVEC") || mot.equals("DE") || mot.equals("TITRE") || mot.equals("AVANT") || mot.equals("PAYS") || mot.equals("APRES") || mot.equals("EN")))
                { // mot clef après un OU ou valeur de champ
                    valeurs.add(value.toString().trim());
                    value = new StringBuilder();
                }
                }
                else if (mot.equals(","))
                { // Si le mot actuel lu est une virgule
                    if (liste_parametres[i+1].equals(","))
                    {
                        this.trigger_error_message = "Une virgule a ete ecrite en trop avant " + liste_parametres[i-1] + ".";
                        this.trigger_error = true;
                        break;
                    }
                    else if (liste_parametres[i+1].equals("OU"))
                    {  // Si on rencontre un 'OU' directement apres une virgule
                        this.trigger_error_message = "Des valeurs sont requises après une ','.";
                        this.trigger_error = true;
                        break;
                    }

                    valeurs.add(value.toString().trim());
                    value = new StringBuilder();

                    switch (champ)
                    {
                        case "TITRE": {
                            if (!premier_where)
                            {
                                sql.append("\nWHERE ((");
                                premier_where = true;
                            }
                            else if (OR_entre_mots_clef)
                            {
                                sql.append("\nOR (");
                            }
                            else
                            {
                                sql.append("\nAND ((");
                            }

                            for (int j = 0; j < valeurs.size(); j++)
                            {
                                if (j > 0)
                                {
                                    sql.append(" OR");
                                }

                                sql.append(" f_table.id_film IN (SELECT id_film FROM recherche_titre rech_titre_table WHERE rech_titre_table.titre LIKE replace(\"%").append(valeurs.get(j)).append("%\", ' ', '%') || '%')");
                            }

                            unique_titre = true;
                            sql.append(")");

                            break;
                        }
                        case "DE":
                        {
                            ArrayList<String> valeurs_2 = new ArrayList<>();

                            for (String tmpVal : valeurs)
                            {
                                if (tmpVal.matches(".*\\d.*"))
                                { // Si la valeur contient un nombre
                                    this.trigger_error_message = "Les valeurs numeriques ne sont pas acceptees pour le mot-clef 'DE'";
                                    this.trigger_error = true;
                                    break;
                                }
                                else valeurs_2.add(tmpVal);
                            }

                            matrice.add(valeurs_2);

                            for (ArrayList<String> strings : matrice)
                            {
                                if (!premier_where)
                                {
                                    sql.append("\nWHERE ((");
                                    premier_where = true;
                                }
                                else if (OR_entre_mots_clef)
                                {
                                    sql.append("\nOR (");
                                }
                                else
                                {
                                    sql.append("\nAND ((");
                                }

                                for (int k = 0; k < strings.size(); k++)
                                {
                                    if (k > 0)
                                    {
                                        sql.append("\nOR");
                                    }

                                    sql.append(" f_table.id_film IN (SELECT id_film FROM personnes NATURAL JOIN generique").append(" WHERE (prenom_sans_accent || ' ' || nom_sans_accent LIKE \"%").append(strings.get(k).replace(' ', '%')).append("%\" OR nom_sans_accent || ' ' || prenom_sans_accent LIKE \"%").append(strings.get(k).replace(' ', '%')).append("%\" OR nom_sans_accent LIKE \"%").append(strings.get(k).replace(' ', '%')).append("%\")").append(" AND role = 'R')");
                                }
                                sql.append(")");
                            }

                            matrice.clear();
                            break;
                        }
                        case "AVEC":
                        {
                            ArrayList<String> valeur_2 = new ArrayList<>();

                            for (String val : valeurs)
                            {
                                if (val.matches(".*\\d.*"))
                                { // Si la valeur contient un nombre
                                    this.trigger_error_message = "Les valeurs numeriques ne sont pas acceptees pour le mot-clef 'AVEC'";
                                    this.trigger_error = true;
                                    break;
                                }
                                else valeur_2.add(val);
                            }

                            matrice.add(valeur_2);

                            for (ArrayList<String> strings : matrice)
                            {
                                if (!premier_where)
                                {
                                    sql.append("\nWHERE ((");
                                    premier_where = true;
                                }
                                else if (OR_entre_mots_clef)
                                {
                                    sql.append("\nOR (");
                                }
                                else
                                {
                                    sql.append("\nAND ((");
                                }

                                for (int k = 0; k < strings.size(); k++)
                                {
                                    if (k > 0){
                                        sql.append("\nOR");
                                    }

                                    sql.append(" f_table.id_film IN (SELECT id_film FROM personnes NATURAL JOIN generique").append(" WHERE (prenom_sans_accent || ' ' || nom_sans_accent LIKE \"%").append(strings.get(k).replace(' ', '%')).append("%\" OR nom_sans_accent || ' ' || prenom_sans_accent LIKE \"%").append(strings.get(k).replace(' ', '%')).append("%\" OR nom_sans_accent LIKE \"%").append(strings.get(k).replace(' ', '%')).append("%\")").append(" AND role = 'A')");
                                }

                                sql.append(")");
                            }

                            matrice.clear();
                            break;
                        }
                        case "PAYS":
                        {
                            if (valeurs.get(0).matches(".*\\d.*"))
                            { // Si la valeur contient un nombre
                                this.trigger_error_message = "Les valeurs numeriques ne sont pas acceptees pour le mot-clef 'PAYS'";
                                this.trigger_error = true;
                                break loop;
                            }
                            else
                            {
                                if (!premier_where)
                                {
                                    sql.append("\nWHERE ((");
                                    premier_where = true;
                                }
                                else if (OR_entre_mots_clef)
                                {
                                    sql.append("\nOR (");
                                }
                                else
                                {
                                    sql.append("\nAND ((");
                                }

                                for (int j = 0; j < valeurs.size(); j++)
                                {
                                    if (j > 0){
                                        sql.append("\nOR");
                                    }

                                    sql.append(" p_table.code LIKE '%").append(valeurs.get(j)).append("%' OR p_table.nom LIKE '%").append(valeurs.get(j)).append("%'");
                                }

                                sql.append(")");
                            }

                            unique_pays = true;
                            break;
                        }
                        case "EN":
                        {
                            ArrayList<Integer> valeurs_2 = new ArrayList<>();

                            for (String val : valeurs) {
                                try {
                                    valeurs_2.add(Integer.valueOf(val));
                                }
                                catch (NumberFormatException err) {
                                    this.trigger_error_message = "Les valeurs chainees ne sont pas acceptees pour le mot-clef EN.";
                                    this.trigger_error = true;
                                    break;
                                }
                            }

                            if (!premier_where)
                            {
                                sql.append("\nWHERE ((");
                                premier_where = true;
                            }
                            else if (OR_entre_mots_clef)
                            {
                                sql.append("\nOR (");
                            }
                            else {
                                sql.append("\nAND ((");
                            }

                            for (int j = 0; j < valeurs_2.size(); j++)
                            {
                                if (j > 0)
                                {
                                    sql.append("\nOR");
                                }

                                sql.append(" f_table.annee = ").append(valeurs_2.get(j));
                            }

                            unique_en = true;
                            sql.append(")");

                            break;
                        }
                        case "AVANT":
                        {
                            ArrayList<Integer> valeurs_2 = new ArrayList<>();

                            for (String tmpVal : valeurs)
                            {
                                try {
                                    valeurs_2.add(Integer.valueOf(tmpVal));
                                }
                                catch (NumberFormatException err)
                                {
                                    this.trigger_error_message = "Les valeurs chainees ne sont pas acceptees pour le mot-clef 'AVANT'.";
                                    this.trigger_error = true;
                                    break;
                                }
                            }

                            if (!premier_where)
                            {
                                sql.append("\nWHERE ((");
                                premier_where = true;
                            }
                            else if (OR_entre_mots_clef)
                            {
                                sql.append("\nOR (");
                            }
                            else {
                                sql.append("\nAND ((");
                            }

                            if (valeurs_2.size() > 0)
                            {
                                sql.append(" f_table.annee < ").append(Collections.max(valeurs_2)).append(")");
                            }

                            break;
                        }
                        case "APRES": {
                            ArrayList<Integer> valeurs_2 = new ArrayList<>();

                            for (String val : valeurs) {
                                try {
                                    valeurs_2.add(Integer.valueOf(val));
                                } catch (NumberFormatException err) {
                                    this.trigger_error_message = "Les valeurs chainees ne sont pas acceptees pour le mot-clef 'APRES'.";
                                    this.trigger_error = true;
                                    break;
                                }
                            }

                            if (!premier_where)
                            {
                                sql.append("\nWHERE ((");
                                premier_where = true;
                            }
                            else if (OR_entre_mots_clef)
                            {
                                sql.append("\nOR (");
                            }
                            else {
                                sql.append("\nAND ((");
                            }

                            if (valeurs_2.size() > 0)
                            {
                                sql.append(" f_table.annee > ").append(Collections.min(valeurs_2)).append(")");
                            }

                            break;
                        }
                    }

                    if (!liste_parametres[i+1].equals("OU") && liste_parametres[i+1] != null)
                    {
                        sql.append(")");
                    }

                    mot_clef_en_cours = true;
                    valeurs.clear();
                }
                else
                {
                    value.append(mot).append(" "); // Si le mot actuel lu fait partie de la valeur du champ comme un nom compose
                }
            }
        }

        sql.append(order_by);

        return sql.toString();
    }

    /**
     * Cette fonction permet de transformer la pseudo-requete en resultat JSON
     * @param pseudorequeteSQL Langage de recherce simplifee
     * Les mots cles autorises sont :
     * TITRE Titre de films
     * DE Noms de realisateurs
     * AVEC Nom d'acteurs
     * PAYS Nom du pays ou du code ISO
     * AVANT Annee de sortie precedentes
     * APRES Annee de sortie suivantes
     * EN Annee de sortie des films
     * @return String -> ResulSet convertit en JSON
     */
    public String retrouve(String pseudorequeteSQL) {
        String requeteSQL = TransformerPseudoSQL(pseudorequeteSQL);
        if (!this.trigger_error){
            return convertToJSON(InfoFilm_tab(requeteSQL));
        }
        else{
            return "{\"trigger_error\":\"" + this.trigger_error_message + "\"}";
        }
    }

    /**
     * Transforme le ResultSet en tableau de String car il est plus facile a manipuler
     * @param resultset ResultSet de la requete SQL
     * @return ArrayList<ArrayList<String>> Tableau de String
     * @throws SQLException ResultSet sans lignes
     */
    public ArrayList<ArrayList<String>> TransformerResultSet(ResultSet resultset) throws SQLException {

        ArrayList<ArrayList<String>> tab = new ArrayList<>();

        do
        {
            ArrayList<String> liste = new ArrayList<>();

            for (int i = 1; i <= 9; i++){
                liste.add(resultset.getString(i));
            }

            tab.add(liste);
        } while (resultset.next());

        resultset.close();
        return tab;
    }

    /**Retourne un tableau d'InfoFilm grace a la requete SQL convertie plus tot
     * @param requete_SQL Requete SQL
     * @return ArrayList<java_project.InfoFilm> Tableau d'InfoFilms
     */
    public ArrayList<InfoFilm> InfoFilm_tab(String requete_SQL){

        int temps;
        int annee;

        ArrayList<InfoFilm> finalListeFilms = new ArrayList<>();

        ArrayList<ArrayList<String>> matrice = this.access_BDD_SQLite.ResultSet_tab(requete_SQL); // ResultSet convertit

        ArrayList<NomPersonne> personnes_realisateurs = new ArrayList<>();
        ArrayList<NomPersonne> personnes_acteurs = new ArrayList<>();

        ArrayList<String> autres_titres = new ArrayList<>();

        String pays, titre;

        for (int i = 0; i < matrice.size(); i++)
        {
            if (matrice.get(i).get(5).equals("A"))
            {
                String prenom_act = "";

                if (matrice.get(i).get(2) != null)
                {
                    prenom_act = matrice.get(i).get(2);
                }

                personnes_acteurs.add(new NomPersonne(prenom_act, matrice.get(i).get(0)));
            }
            else if (matrice.get(i).get(5).equals("R"))
            {
                String prenom_real = "";
                if (matrice.get(i).get(2) != null)
                {
                    prenom_real = matrice.get(i).get(2);
                }

                personnes_realisateurs.add(new NomPersonne(prenom_real, matrice.get(i).get(0)));
            }

            titre = matrice.get(i).get(3);

            if (matrice.get(i).get(6) != null)
            {
                temps = Integer.valueOf(matrice.get(i).get(6));
            }
            else {
                temps = 0;
            }

            if (matrice.get(i).get(7) != null)
            {
                annee = Integer.valueOf(matrice.get(i).get(7));
            }
            else {
                annee = 0;
            }

            if (matrice.get(i).get(1) != null)
            {
                pays = matrice.get(i).get(1);
            }
            else {
                pays = "";
            }

            if (autres_titres.isEmpty() && matrice.get(i).get(8) != null)
            {
                String[] autres_titres_split = matrice.get(i).get(8).split("#");
                Collections.addAll(autres_titres, autres_titres_split);
            }

            // Nouveau film lu ou fin de la liste : on cree et ajoute une nouvelle instance de java_project.InfoFilm dans l'ArrayList
            if (i == (matrice.size()-1) || !Integer.valueOf(matrice.get(i).get(4)).equals(Integer.valueOf(matrice.get(i + 1).get(4))))
            {
                finalListeFilms.add(new InfoFilm(titre, personnes_realisateurs, personnes_acteurs, pays, annee, temps, autres_titres));

                personnes_acteurs = new ArrayList<>();
                personnes_realisateurs = new ArrayList<>();
                autres_titres = new ArrayList<>();
            }
        }

        this.access_BDD_SQLite.Fermeture_BDD_SQLite();
        return finalListeFilms;
    }

    /**
     * Formater correctement le JSON
     * @param list Tableau d'InfoFilm
     * @return String JSON
     */
    public String convertToJSON(ArrayList<InfoFilm> list) {
        StringBuilder result = new StringBuilder();

        int n = 0;

        if (list.size() >= 100)
        {
            n = 100;
            result.append("{\"info\":\"Affiche les 100 premiers films.\", \"resultat\":[ ");
        }
        else
        {
            n = list.size();
            result.append("{\"resultat\":[");
        }

        for (int i = 0; i < n; i++)
        {
            if (i > 0){
                result.append(",\n");
            }

            result.append(list.get(i).toString());
        }

        result.append("]}");

        return result.toString();
    }
}
