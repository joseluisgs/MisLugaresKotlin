package com.joseluisgs.mislugares.Entidades.Sesiones

import io.realm.Realm
import io.realm.kotlin.where

/**
* Controlador de Sesion
*/
object SesionController  {
      /**
     * Inserta una sesion
     * @param sesion Sesion
     */
    fun insert(sesion: Sesion) {
        Realm.getDefaultInstance().executeTransaction {
            it.copyToRealm(sesion); // Copia, inserta
        }
    }

    /**
     * Borra un lugar del sistema
     * @param sesion Sesion
     */
    fun delete(sesion: Sesion) {
        Realm.getDefaultInstance().executeTransaction {
            it.where<Sesion>().equalTo("usuarioID", sesion.usuarioID).findFirst()?.deleteFromRealm()
        }
    }

    fun deleteByID(usuarioID: String) {
        Realm.getDefaultInstance().executeTransaction {
            it.where<Sesion>().equalTo("usuarioID", usuarioID).findFirst()?.deleteFromRealm()
        }
    }


    /**
     * Busca lugar por id
     * @param login String
     * @return Lugar?
     */
    fun selectById(usuarioID: String): Sesion? {
        return Realm.getDefaultInstance().copyFromRealm(
            Realm.getDefaultInstance().where<Sesion>().equalTo("usuarioID", usuarioID).findFirst()
        )
    }

    /**
     * Te devuelve la sesion actual
     * @return Sesion?
     */
    fun getFirst(): Sesion? {
        return Realm.getDefaultInstance().copyFromRealm(
            Realm.getDefaultInstance().where<Sesion>().findFirst()
        )
    }

    /**
     * Te devuelve la sesion actual
     * @return Sesion?
     */
    fun selectAll(): MutableList<Sesion>? {
        return Realm.getDefaultInstance().copyFromRealm(
            Realm.getDefaultInstance().where<Sesion>().findAll()
        )
    }

    /**
     * Elimina todos los objetos
     */
    fun removeAll() {
        Realm.getDefaultInstance().executeTransaction {
            Realm.getDefaultInstance().where<Sesion>().findAll().deleteAllFromRealm();
        }
    }
}