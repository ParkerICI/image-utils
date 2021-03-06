(ns org.parkerici.image-utils.ij.core
  (:require [org.parkerici.image-utils.ij.hyperstack :as hyperstack]
            [org.parkerici.image-utils.ij.tiled :as tiled]
            [org.parkerici.image-utils.ij.io :as ij-io]
            [org.parkerici.image-utils.utils.path :as path]
            [me.raynes.fs :as fs]))

(defn print-image-info
  [img]
  (println "Image info:")
  (println (str "Channels: " (.getChannels img)))
  (println (str "Depth: " (.getDepth img)))
  (println (str "Frames: " (.getFrames img)))
  (println (str "Height: " (.getHeight img)))
  (println (str "Width: " (.getWidth img)))
  (println (str "Dimensions: " (.numDimensions img)))
  (println (str "Slices: " (.numSlices img) "\n")))

(defn get-create-fdir
  [output-to-subfolder outpath img-base-name]
  (let [fdir (if output-to-subfolder (path/join outpath img-base-name) outpath)]
    (fs/mkdirs fdir)
    fdir))

(defn output-hyperstack-slice
  [source-img slice dimension img-base-name img-extension fdir output-to-subfolder]
  (let [fname (if output-to-subfolder (str dimension img-extension) (str img-base-name "_" dimension img-extension))
        clean-fname (path/sanitize-filename fname)
        fpath (path/join fdir clean-fname)
        slice-img (hyperstack/slice->img source-img slice)]
    (ij-io/write-tiff slice-img fpath)))

(defn output-tiled-slice
  [slice dimension img-base-name img-extension fdir output-to-subfolder]
  (let [fname (if output-to-subfolder (str dimension img-extension) (str img-base-name "_" dimension img-extension))
        clean-fname (path/sanitize-filename fname)
        fpath (path/join fdir clean-fname)]
    (tiled/save-imp-as-tiff  slice fpath)))

(defn split-hyperstack-file
  [fpath outpath output-to-subfolder]
   (println (str "\nReading image at " fpath))
   (let [img-base-name (fs/base-name fpath true)
         img-extension (fs/extension fpath)
         img (ij-io/read-tiff fpath)
         slices (hyperstack/img->slices img)
         fdir (get-create-fdir output-to-subfolder (or outpath (.getPath (fs/parent fpath))) img-base-name)]
        (println (str "Writing outputs to " fdir "\n"))
     (print-image-info img)
     (println "Writing slices to files. Warning: this may take a while.")
     (doall (pmap
             #(output-hyperstack-slice img (get slices %) % img-base-name img-extension fdir output-to-subfolder)
             (keys slices)))))

(defn split-tiled-file
  [fpath outpath output-to-subfolder]
  (println (str "\nReading image at " fpath))
  (let [img-base-name (fs/base-name fpath true)
        img-extension (fs/extension fpath)
        img (tiled/open-tiled-tiff fpath)
        slices (tiled/split-channels img)
        channel-names (tiled/get-channels img)
        slices-map (zipmap channel-names slices)
        fdir (get-create-fdir output-to-subfolder (or outpath (.getPath (fs/parent fpath))) img-base-name)]
    (println (str "Writing outputs to " fdir "\n"))
    ;(print-image-info img)
    (println "Writing slices to files. Warning: this may take a while.")
    (doall (pmap
             #(output-tiled-slice (get slices-map %) % img-base-name img-extension fdir output-to-subfolder)
             (keys slices-map)
             ))))

(defn tiffs-in-dir
  [fpath]
  (remove nil? (flatten (into [] (for [ext-pattern ["*.tif" "*.TIF" "*.tiff" "*.TIFF"]]
             (fs/glob (fs/file fpath) ext-pattern))))))

(defn split-hyperstack-dir
  [fpath outpath output-to-subfolder]
  (doall (for [tiff (tiffs-in-dir fpath)]
           (split-hyperstack-file (.getPath tiff) outpath output-to-subfolder))))

(defn split-tiled-dir
  [fpath outpath output-to-subfolder]
  (doall (for [tiff (tiffs-in-dir fpath)]
           (split-tiled-file (.getPath tiff) outpath output-to-subfolder))))

(defn split-hyperstack
  [fpath outpath output-to-subfolder]
  (if (fs/file? fpath)
    (split-hyperstack-file fpath outpath output-to-subfolder)
    (split-hyperstack-dir fpath outpath output-to-subfolder)))

(defn split-tiled
  [fpath outpath output-to-subfolder]
  (if (fs/file? fpath)
    (split-tiled-file fpath outpath output-to-subfolder)
    (split-tiled-dir fpath outpath output-to-subfolder)))
