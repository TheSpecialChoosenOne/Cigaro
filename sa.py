import os

def merge_everything(source_folder, output_file):
    output_abs_path = os.path.abspath(output_file)
    files_count = 0

    with open(output_file, 'w', encoding='utf-8') as outfile:
        for root, dirs, files in os.walk(source_folder):
            for filename in files:
                file_path = os.path.join(root, filename)
                abs_path = os.path.abspath(file_path)

                if abs_path == output_abs_path:
                    continue

                try:
                    with open(file_path, 'r', encoding='utf-8', errors='replace') as infile:
                        content = infile.read()

                        outfile.write(f"\n\n{'='*70}\n")
                        outfile.write(f"ПУТЬ: {file_path}\n")
                        outfile.write(f"{'='*70}\n\n")

                        outfile.write(content)
                        files_count += 1
                        print(f"Добавлен: {file_path}")

                except Exception as e:
                    print(f"Ошибка при чтении {file_path}: {e}")

    print(f"\n--- ГОТОВО ---")
    print(f"Всего обработано файлов: {files_count}")
    print(f"Результат сохранен в: {output_abs_path}")

folder_to_process = './src'
result_name = 'combined_data.txt'

merge_everything(folder_to_process, result_name)