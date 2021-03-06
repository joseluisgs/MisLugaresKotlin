package com.joseluisgs.mislugares.UI.lugares

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.joseluisgs.mislugares.Entidades.Lugares.Lugar
import com.joseluisgs.mislugares.R
import com.joseluisgs.mislugares.UI.lugares.filtro.Filtro
import com.joseluisgs.mislugares.UI.lugares.filtro.FiltroController
import kotlinx.android.synthetic.main.fragment_lugar_detalle.*
import kotlinx.android.synthetic.main.fragment_lugares.*
import java.text.SimpleDateFormat
import java.util.*


class LugaresFragment : Fragment() {
    // Firebase
    private lateinit var Auth: FirebaseAuth
    private lateinit var FireStore: FirebaseFirestore

    // Propiedades
    private var LUGARES = mutableListOf<Lugar>()
    private lateinit var lugaresAdapter: LugaresListAdapter //Adaptador de Noticias de Recycler
    private var paintSweep = Paint()
    private lateinit var USUARIO: FirebaseUser

    // Búsquedas
    private var FILTRO = Filtro.NADA
    private val VOZ = 10

    companion object {
        private const val TAG = "Lugares"
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_lugares, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Servicios de Firebase
        Auth = Firebase.auth
        FireStore = FirebaseFirestore.getInstance()
        // Iniciamos la interfaz
        this.USUARIO = Auth.currentUser!!
        initUI()
    }

    /**
     * Inicia la Interfaz de Usuario
     */
    private fun initUI() {
        Log.i(TAG, "Init IU")
        iniciarSwipeRecarga()
        cargarLugares()
        iniciarSpinner()
        iniciarSwipeHorizontal()
        lugaresRecycler.layoutManager = LinearLayoutManager(context)
        lugaresFabNuevo.setOnClickListener { nuevoElemento() }
        lugaresButtonVoz.setOnClickListener { controlPorVoz() }

        Log.i(TAG, "Fin la IU")
    }

    /**
     * Inicia el Spinner
     */
    private fun iniciarSpinner() {
        val tipoBusqueda = resources.getStringArray(R.array.tipos_busqueda)
        val adapter = ArrayAdapter(context!!,
            android.R.layout.simple_spinner_item, tipoBusqueda)
        lugaresSpinnerFiltro.adapter = adapter
        lugaresSpinnerFiltro.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View, position: Int, id: Long,
            ) {
                FILTRO = FiltroController.analizarFiltroSpinner(position)
                // Listamos los lugares y cargamos el recycler
                Log.i("Filtro", position.toString())
                Toast.makeText(context!!, "Ordenando por: " + tipoBusqueda[position], Toast.LENGTH_SHORT).show()
                visualizarListaItems()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // write code to perform some action
            }
        }
    }

    /**
     * Deslizamiento vertical para recargar
     */
    private fun iniciarSwipeRecarga() {
        lugaresSwipeRefresh.setColorSchemeResources(R.color.colorPrimaryDark)
        lugaresSwipeRefresh.setProgressBackgroundColorSchemeResource(R.color.colorAccent)
        lugaresSwipeRefresh.setOnRefreshListener {
            cargarLugares()
        }
    }

    /**
     * Realiza el swipe horizontal si es necesario
     */
    private fun iniciarSwipeHorizontal() {
        val simpleItemTouchCallback: ItemTouchHelper.SimpleCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or
                    ItemTouchHelper.RIGHT
        ) {
            // Sobreescribimos los métodos
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                return false
            }

            // Analizamos el evento según la dirección
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                // Si pulsamos a la de izquierda o a la derecha
                // Programamos la accion
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        borrarElemento(position)
                    }
                    else -> {
                        editarElemento(position)
                    }
                }
            }

            // Dibujamos los botones y evento. Nos lo creemos :):)
            // IMPORTANTE
            // Para que no te explote las imagenes deben ser PNG
            // Así que añade un IMAGE ASEET bjándtelos de internet
            // https://material.io/resources/icons/?style=baseline
            // como PNG y cargas el de mayor calidad
            // de otra forma Bitmap no funciona bien
            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val height = itemView.bottom.toFloat() - itemView.top.toFloat()
                    val width = height / 3
                    // Si es dirección a la derecha: izquierda->derecha
                    // Pintamos de azul y ponemos el icono
                    if (dX > 0) {
                        // Pintamos el botón izquierdo
                        botonIzquierdo(canvas, dX, itemView, width)
                    } else {
                        // Caso contrario
                        botonDerecho(canvas, dX, itemView, width)
                    }
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        // Añadimos los eventos al RV
        val itemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        itemTouchHelper.attachToRecyclerView(lugaresRecycler)
    }

    /**
     * Mostramos el elemento izquerdo
     * @param canvas Canvas
     * @param dX Float
     * @param itemView View
     * @param width Float
     */
    private fun botonDerecho(canvas: Canvas, dX: Float, itemView: View, width: Float) {
        // Pintamos de rojo y ponemos el icono
        paintSweep.color = Color.RED
        val background = RectF(
            itemView.right.toFloat() + dX,
            itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat()
        )
        canvas.drawRect(background, paintSweep)
        val icon: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_seep_eliminar)
        val iconDest = RectF(
            itemView.right.toFloat() - 2 * width, itemView.top.toFloat() + width, itemView.right
                .toFloat() - width, itemView.bottom.toFloat() - width
        )
        canvas.drawBitmap(icon, null, iconDest, paintSweep)
    }

    /**
     * Mostramos el elemento izquierdo
     * @param canvas Canvas
     * @param dX Float
     * @param itemView View
     * @param width Float
     */
    private fun botonIzquierdo(canvas: Canvas, dX: Float, itemView: View, width: Float) {
        // Pintamos de azul y ponemos el icono
        paintSweep.color = Color.BLUE
        val background = RectF(
            itemView.left.toFloat(), itemView.top.toFloat(), dX,
            itemView.bottom.toFloat()
        )
        canvas.drawRect(background, paintSweep)
        val icon: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_sweep_editar)
        val iconDest = RectF(
            itemView.left.toFloat() + width, itemView.top.toFloat() + width, itemView.left
                .toFloat() + 2 * width, itemView.bottom.toFloat() - width
        )
        canvas.drawBitmap(icon, null, iconDest, paintSweep)
    }

    /**
     * Abre un nuevo elemeneto
     */
    private fun nuevoElemento() {
        Log.i(TAG, "Nuevo lugar")
        abrirDetalle(null, Modo.INSERTAR)
    }

    /**
     * Inserta un elemento en la lista
     */
    private fun insertarItemLista(item: Lugar) {
        this.lugaresAdapter.addItem(item)
        lugaresAdapter.notifyDataSetChanged()
    }

    /**
     * Edita el elemento en la posición seleccionada
     * @param position Int
     */
    private fun editarElemento(position: Int) {
        Log.i(TAG, "Editando el elemento pos: $position")
        // Para que no se quede el elemento de modificar o borrar marcado al deslizar
        actualizarVistaLista()
        abrirDetalle(LUGARES[position], Modo.ACTUALIZAR)
    }

    /**
     * Actualiza la lista de items
     * @param item Lugar
     * @param position Int
     */
    private fun actualizarItemLista(item: Lugar, position: Int) {
        this.lugaresAdapter.updateItem(item, position)
        lugaresAdapter.notifyDataSetChanged()
    }

    /**
     * Borra el elemento en la posición seleccionada
     * @param position Int
     */
    private fun borrarElemento(position: Int) {
        Log.i(TAG, "Borrando el elemento pos: $position")
        // Para que no se quede el elemento de modificar o borrar marcado al deslizar
        abrirDetalle(LUGARES[position], Modo.ELIMINAR)
    }

    /**
     * Elimina un elemento de la vista
     * @param position Int
     */
    private fun eliminarItemLista(position: Int) {
        this.lugaresAdapter.removeItem(position)
        lugaresAdapter.notifyDataSetChanged()
    }

    /**
     * Actualizamos la vista para evitar que se quede pulsado al deslizar.
     */
    private fun actualizarVistaLista() {
        lugaresRecycler.adapter = lugaresAdapter
    }

    /**
     * Abre el elemento en la posición didicada
     * @param lugar Lugar
     */
    private fun abrirElemento(lugar: Lugar) {
        Log.i(TAG, "Visualizando el elemento: ${lugar.id}")
        abrirDetalle(lugar, Modo.VISUALIZAR)
    }

    /**
     * Abre el detalle del item
     * @param lugar Lugar?
     * @param modo Modo?
     */
    private fun abrirDetalle(lugar: Lugar?, modo: Modo?) {
        Log.i("Lugares", "Abriendo el elemento pos: " + lugar?.id)
        val lugarDetalle = LugarDetalleFragment(lugar, modo)
        val transaction = activity!!.supportFragmentManager.beginTransaction()
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        transaction.add(R.id.nav_host_fragment, lugarDetalle)
        transaction.addToBackStack(null)
        transaction.commit()
        // Para evitar que se quede pulsado un elemento que deslizamos
        actualizarVistaLista()
    }

    /**
     * Evento clic asociado a una fila
     * @param lugar Lugar
     */
    private fun eventoClicFila(lugar: Lugar) {
        abrirElemento(lugar)
    }

    /**
     * Dispara el control por voz
     */
    private fun controlPorVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.texto_ordenacion_voz))
        try {
            startActivityForResult(intent, VOZ)
        } catch (e: java.lang.Exception) {
        }
    }

    /**
     * Resultadp de las acciones
     * @param requestCode Int
     * @param resultCode Int
     * @param data Intent?
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_CANCELED) {
            return
        }
        if (requestCode == VOZ) {
            if (resultCode == RESULT_OK && data != null) {
                val voz = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                // Analizamos los que nos puede llegar
                var secuencia = ""
                // Concatenamos todo lo que tiene la cadena encontrada para buscar palabras clave
                for (v in voz!!) {
                    secuencia += " $v"
                }
                // A partir de aquí podemos crear el if todo lo complejo que queramos o irnos a otro fichero
                if (secuencia.isNotEmpty()) {
                    analizarFiltroVoz(secuencia)
                    visualizarListaItems()
                }
            }
        }
    }

    /**
     * Analiza el resultado de procesar la voz
     *
     * @param secuencia Sencuencia de entrada
     * @return filtro de salida
     */
    private fun analizarFiltroVoz(secuencia: String) {
        // Nombre
        FILTRO = FiltroController.analizarFiltroSecuencia(secuencia)
    }


    /**
     * función para ordenar la lista como mayores y menores
     */
    private fun ordenarLugares() {
        Log.i("Filtro", FILTRO.toString())
        when (FILTRO) {
            Filtro.NADA -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar -> l1.id.compareTo(l2.id) }
            // Nombre
            Filtro.NOMBRE_ASC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar ->
                l1.nombre.toLowerCase().compareTo(l2.nombre.toLowerCase())
            }
            Filtro.NOMBRE_DESC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar ->
                l2.nombre.toLowerCase().compareTo(l1.nombre.toLowerCase())
            }

            // Tipo
            Filtro.TIPO_ASC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar -> l1.tipo.compareTo(l2.tipo) }
            Filtro.TIPO_DESC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar -> l2.tipo.compareTo(l1.tipo) }

            // Fecha
            Filtro.FECHA_ASC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar ->
                SimpleDateFormat("dd/MM/yyyy").parse(l1.fecha).compareTo(SimpleDateFormat("dd/MM/yyyy").parse(l2.fecha))
            }
            Filtro.FECHA_DESC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar ->
                SimpleDateFormat("dd/MM/yyyy").parse(l2.fecha).compareTo(SimpleDateFormat("dd/MM/yyyy").parse(l1.fecha))
            }

            // Favoritos
            Filtro.FAVORITO_ASC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar -> l1.favorito.compareTo(l2.favorito) }
            Filtro.FAVORITO_DESC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar -> l2.favorito.compareTo(l1.favorito) }

            // Votos
            Filtro.VOTOS_ASC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar -> l1.votos.compareTo(l2.votos) }
            Filtro.VOTOS_DESC -> this.LUGARES.sortWith { l1: Lugar, l2: Lugar -> l2.votos.compareTo(l1.votos) }
        }
    }

    /**
     * Carga los lugares
     */
    private fun cargarLugares() {
        LUGARES.clear()
        lugaresSwipeRefresh.isRefreshing = true
        lugaresAdapter = LugaresListAdapter(LUGARES) {
            eventoClicFila(it)
        }
        lugaresRecycler.adapter = lugaresAdapter
        Toast.makeText(context, "Obteniendo lugares", Toast.LENGTH_LONG).show()
        // Podemos hacerlo de dos maneras, manual o suscribirnos en tiempo real
        // https://firebase.google.com/docs/firestore/query-data/get-data
        // https://firebase.google.com/docs/firestore/query-data/listen
        // Yo lo voy a hacer en tiempo real. Pero debes sopesar esta decisión
        // Si hubiese varios clientes y los datos fuesen compartidos, los detectaría sin recargar (swipe).
        // Esto nos ahorra otro codigo que teneiamos por otro lado (lugardetallefragmen) donde obligamos a actualizar la Interfaz
        // Ahora lo hace,os todo al estar suscritos desde aquí
        FireStore.collection("lugares")
            .addSnapshotListener { value, e ->
                if (e != null) {
                    Toast.makeText(context,
                        "Error al acceder al servicio: " + e.localizedMessage,
                        Toast.LENGTH_LONG)
                        .show()
                    return@addSnapshotListener
                }
                // LUGARES.clear()
                lugaresSwipeRefresh.isRefreshing = false
                for (doc in value!!.documentChanges) {
                    when (doc.type) {
                        DocumentChange.Type.ADDED -> {
                            Log.i(TAG, "ADDED  ${doc.document.data}")
                            insertarDocumento(doc.document.data)
                        }
                        DocumentChange.Type.MODIFIED -> {
                            Log.i(TAG, "MODIFIED: ${doc.document.data}")
                            modificarDocumento(doc.document.data)
                        }
                        DocumentChange.Type.REMOVED -> {
                            Log.i(TAG, "REMOVED: ${doc.document.data}")
                            eliminarDocumento(doc.document.data)
                        }
                    }
                }
            }
        // Sin tiempo real, entonces si necesiariamos refrescar y tener metodos en detalle que aactualicen la lista
        /*  FireStore.collection("lugares")
              .get()
              .addOnSuccessListener { result ->
                  LUGARES = result.toObjects(Lugar::class.java)
                  Log.i(TAG, "Lista de lugares de tamaño: " + LUGARES.size)
                  procesarLugares()
              }
              .addOnFailureListener { exception ->
                  Toast.makeText(context,
                      "Error al acceder al servicio: " + exception.localizedMessage,
                      Toast.LENGTH_LONG)
                      .show()
              }*/
    }

    /**
     * Elimina un dato de una lista
     * @param doc Map<String, Any>
     */
    private fun eliminarDocumento(doc: Map<String, Any>) {
        val miLugar = documentToLugar(doc)
        Log.i(TAG, "Elimando lugar: ${miLugar.id}")
        // Buscamos que esté
        val index = LUGARES.indexOf(miLugar)
        if (index >= 0)
            eliminarItemLista(index)
    }


    /**
     * Modifica el dato de una lista
     * @param doc Map<String, Any>
     */
    private fun modificarDocumento(doc: Map<String, Any>) {
        val miLugar = documentToLugar(doc)
        Log.i(TAG, "Modificando lugar: ${miLugar.id}")
        // Buscamos que esté,
        val index = LUGARES.indexOf(miLugar)
        if (index >= 0)
            actualizarItemLista(miLugar, index)
    }

    /**
     * Devuelve un lugar de un documento (mapa)
     * @param doc Map<String, Any>
     * @return Lugar
     */
    private fun documentToLugar(doc: Map<String, Any>) = Lugar(
        id = doc["id"].toString(),
        nombre = doc["nombre"].toString(),
        tipo = doc["tipo"].toString(),
        fecha = doc["fecha"].toString(),
        latitud = doc["latitud"].toString(),
        longitud = doc["longitud"].toString(),
        imagenID = doc["imagenID"].toString(),
        favorito = (doc["favorito"].toString().toBoolean()),
        votos = doc["votos"].toString().toInt(),
        time = doc["time"].toString(),
        usuarioID = doc["usuarioID"].toString()
    )

    /**
     * Inserta el dato en una lista
     * @param doc MutableMap<String, Any>
     */
    private fun insertarDocumento(doc: MutableMap<String, Any>) {
        val miLugar = documentToLugar(doc)
        Log.i(TAG, "Añadiendo lugar: ${miLugar.id}")
        // Buscamos que no este... para evitar distintas llamadas de ADD
        val existe = LUGARES.any { lugar -> lugar.id == miLugar.id }
        if (!existe)
            insertarItemLista(miLugar)
    }


    /**
     * Visualiza la lista de items
     */
    private fun visualizarListaItems() {
        ordenarLugares()
        try {
            actualizarVistaLista()
        } catch (ex: Exception) {
        }
    }
}

