(ns alandipert.waffle
  (:require [alandipert.priority-map :refer [priority-map]]
            [alandipert.desiderata   :as    d]))

(let [rank  (atom 0)
      stamp (atom 0)]
  (def next-rank  #(swap! rank inc))
  (def next-stamp #(swap! stamp inc)))

(defrecord EventStream [sinks rank update-fn])

(defrecord Pulse [stamp value])

(defn make-event-stream
  [sinks update-fn]
  (atom (EventStream. sinks (next-rank) update-fn)))

(defn make-pulse
  [value]
  (Pulse. (next-stamp) value))

(def stop ::stop)

(defn propagate!
  [node pulse]
  (loop [queue (priority-map {:node node :pulse pulse} (:rank @node))]
    (if (seq queue)
      (let [{:keys [node pulse]} (key (peek queue))
            next-pulse ((:update-fn @node) pulse)]
        (if (not= next-pulse stop)
          (recur (reduce #(assoc %1 {:node %2 :pulse next-pulse} (:rank @%2))
                         (pop queue)
                         (:sinks @node))))))))

(defn add-sink!
  [e dependent]
  (swap! e update-in [:sinks] conj dependent)
  (if (> (:rank @e) (:rank @dependent))
    (doseq [dep (d/bf-seq identity :sinks dependent)]
      (swap! dep assoc :rank (next-rank)))))

(defn remove-sink!
  [e dependent]
  (swap! e update-in [:sinks] #(filterv (partial not= dependent) %)))

(defn event-stream
  [nodes update-fn]
  (let [e (make-event-stream [] update-fn)]
    (doseq [node nodes] (add-sink! node e))
    e))

(defn internalE
  ([] (internalE []))
  ([depends-on] (event-stream depends-on identity)))

(def receiverE internalE)

(defn send-event!
  [node value]
  (propagate! node (make-pulse value)))

(defn zeroE
  "Create an event stream that never fires any events."
  []
  (event-stream
   []
   #(throw
     (js/Error.
      (str "zeroE : should not have received a value; the value was " (pr-str %))))))

(defn mergeE
  [& es]
  (if (seq es) (internalE es) (zeroE)))

(defn constantE
  [e constant-value]
  (event-stream [e] #(assoc % :value constant-value)))

(defn mapE
  [e f]
  (event-stream [e] #(update-in % [:value] f)))

(defn tapE
  "Presumes f is for side effects and discards its value. To preserve
  consistency, f must not call send-event! or cause it to be called."
  [e f]
  (mapE e #(do (f %) %)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn doit []
  (let [e1 (receiverE)
        e2 (-> e1
               (mapE #(.toUpperCase %))
               (tapE #(js/alert %))
               (tapE #(js/document.write %)))]
    (doseq [c "omg"]
      (send-event! e1 c))))